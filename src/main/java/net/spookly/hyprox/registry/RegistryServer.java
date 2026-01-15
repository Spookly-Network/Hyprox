package net.spookly.hyprox.registry;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.util.CidrMatcher;
import net.spookly.hyprox.util.ListenAddress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;

public final class RegistryServer {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private final HyproxConfig config;
    private final BackendRegistry registry;
    private final HttpServer server;
    private final CidrMatcher networkMatcher;
    private final NonceCache nonceCache;

    public RegistryServer(HyproxConfig config, BackendRegistry registry) {
        this.config = Objects.requireNonNull(config, "config");
        this.registry = Objects.requireNonNull(registry, "registry");
        ListenAddress listenAddress = ListenAddress.parse(config.registry.listen);
        InetSocketAddress socketAddress = listenAddress.toSocketAddress();
        try {
            this.server = HttpServer.create(socketAddress, 0);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to bind registry listener", e);
        }
        this.server.setExecutor(Executors.newFixedThreadPool(4));
        this.server.createContext("/v1/registry/register", new RegisterHandler());
        this.server.createContext("/v1/registry/heartbeat", new HeartbeatHandler());
        this.server.createContext("/v1/registry/drain", new DrainHandler());
        this.server.createContext("/v1/registry/backends", new ListHandler());
        this.networkMatcher = CidrMatcher.from(config.registry.allowedNetworks);
        int clockSkew = 10;
        if (config.registry.auth != null && config.registry.auth.clockSkewSeconds != null) {
            clockSkew = config.registry.auth.clockSkewSeconds;
        }
        int nonceTtl = Math.max(1, clockSkew * 2 + 1);
        this.nonceCache = new NonceCache(nonceTtl);
    }

    public void start() {
        registry.start();
        server.start();
        System.out.println("Registry listening on " + server.getAddress().getHostString() + ":" + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        registry.stop();
    }

    private abstract class BaseHandler implements HttpHandler {
        @Override
        public final void handle(HttpExchange exchange) throws IOException {
            try {
                byte[] body = readBodyBytes(exchange);
                if (!isAllowedRemote(exchange)) {
                    writeResponse(exchange, 403, RegistryResponse.error("remote address not allowed"));
                    return;
                }
                if (!authorize(exchange, body)) {
                    return;
                }
                handleAuthorized(exchange, body);
            } catch (IllegalArgumentException e) {
                writeResponse(exchange, 400, RegistryResponse.error(e.getMessage()));
            } catch (Exception e) {
                writeResponse(exchange, 500, RegistryResponse.error("internal error"));
            } finally {
                exchange.close();
            }
        }

        protected abstract void handleAuthorized(HttpExchange exchange, byte[] body) throws IOException;

        protected <T> T readJson(byte[] payload, Class<T> type) throws IOException {
            if (payload == null || payload.length == 0) {
                throw new IllegalArgumentException("request body required");
            }
            return MAPPER.readValue(payload, type);
        }

        protected void writeResponse(HttpExchange exchange, int status, RegistryResponse response) throws IOException {
            byte[] payload = MAPPER.writeValueAsBytes(response);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(payload);
            }
        }

        protected byte[] readBodyBytes(HttpExchange exchange) throws IOException {
            try (InputStream input = exchange.getRequestBody()) {
                if (input == null) {
                    return new byte[0];
                }
                return input.readAllBytes();
            }
        }
    }

    private final class RegisterHandler extends BaseHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, byte[] body) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeResponse(exchange, 405, RegistryResponse.error("method not allowed"));
                return;
            }
            RegistryRequests.RegisterRequest request = readJson(body, RegistryRequests.RegisterRequest.class);
            validateOrchestrator(request.orchestratorId, exchange);
            validatePool(request.pool, request.orchestratorId);
            validateBackendId(request.backendId, request.orchestratorId);
            requireNonBlank(request.host, "host");
            int port = requirePort(request.port, "port");
            validateBackendHost(request.host);

            RegisteredBackend backend = new RegisteredBackend(
                    request.backendId,
                    request.pool,
                    request.host,
                    port,
                    request.weight,
                    request.maxPlayers,
                    request.tags,
                    request.orchestratorId,
                    Instant.now(),
                    Instant.now(),
                    false
            );
            RegisteredBackend stored = registry.register(backend, request.ttlSeconds);
            Map<String, Object> data = new HashMap<>();
            data.put("backendId", stored.id());
            data.put("pool", stored.pool());
            data.put("expiresAt", stored.expiresAt().toString());
            writeResponse(exchange, 200, RegistryResponse.ok("registered", data));
        }
    }

    private final class HeartbeatHandler extends BaseHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, byte[] body) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeResponse(exchange, 405, RegistryResponse.error("method not allowed"));
                return;
            }
            RegistryRequests.HeartbeatRequest request = readJson(body, RegistryRequests.HeartbeatRequest.class);
            validateOrchestrator(request.orchestratorId, exchange);
            requireNonBlank(request.backendId, "backendId");
            RegisteredBackend backend = registry.heartbeat(request.backendId, request.orchestratorId, request.ttlSeconds);
            Map<String, Object> data = new HashMap<>();
            data.put("backendId", backend.id());
            data.put("expiresAt", backend.expiresAt().toString());
            writeResponse(exchange, 200, RegistryResponse.ok("heartbeat", data));
        }
    }

    private final class DrainHandler extends BaseHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, byte[] body) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeResponse(exchange, 405, RegistryResponse.error("method not allowed"));
                return;
            }
            RegistryRequests.DrainRequest request = readJson(body, RegistryRequests.DrainRequest.class);
            validateOrchestrator(request.orchestratorId, exchange);
            requireNonBlank(request.backendId, "backendId");
            RegisteredBackend backend = registry.drain(request.backendId, request.orchestratorId, request.drainSeconds);
            Map<String, Object> data = new HashMap<>();
            data.put("backendId", backend.id());
            data.put("draining", backend.draining());
            data.put("expiresAt", backend.expiresAt().toString());
            writeResponse(exchange, 200, RegistryResponse.ok("drain", data));
        }
    }

    private final class ListHandler extends BaseHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, byte[] body) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeResponse(exchange, 405, RegistryResponse.error("method not allowed"));
                return;
            }
            String orchestratorId = exchange.getRequestHeaders().getFirst("X-Hyprox-Orchestrator");
            validateOrchestrator(orchestratorId, exchange);
            URI uri = exchange.getRequestURI();
            String pool = Optional.ofNullable(uri.getQuery())
                    .map(q -> q.split("=") )
                    .filter(parts -> parts.length == 2 && "pool".equalsIgnoreCase(parts[0]))
                    .map(parts -> parts[1])
                    .orElse(null);
            List<RegisteredBackend> results = registry.list(pool);
            Map<String, Object> data = new HashMap<>();
            data.put("count", results.size());
            data.put("backends", toView(results));
            writeResponse(exchange, 200, RegistryResponse.ok("ok", data));
        }
    }

    private boolean authorize(HttpExchange exchange, byte[] body) throws IOException {
        if (config.registry == null || config.registry.auth == null) {
            writeResponse(exchange, 500, RegistryResponse.error("registry auth config missing"));
            return false;
        }
        if ("hmac".equalsIgnoreCase(config.registry.auth.mode)) {
            return authorizeHmac(exchange, body);
        }
        writeResponse(exchange, 501, RegistryResponse.error("registry auth mode not implemented"));
        return false;
    }

    private boolean authorizeHmac(HttpExchange exchange, byte[] body) throws IOException {
        Headers headers = exchange.getRequestHeaders();
        String timestampRaw = headers.getFirst("X-Hyprox-Timestamp");
        Long timestamp = null;
        if (timestampRaw != null) {
            try {
                timestamp = Long.parseLong(timestampRaw);
            } catch (NumberFormatException ignored) {
                timestamp = null;
            }
        }
        String nonce = headers.getFirst("X-Hyprox-Nonce");
        String signature = headers.getFirst("X-Hyprox-Signature");
        String orchestratorId = headers.getFirst("X-Hyprox-Orchestrator");
        String path = rawPath(exchange.getRequestURI());
        HmacAuth.RequestAuthData authData = new HmacAuth.RequestAuthData(
                exchange.getRequestMethod(),
                path,
                timestamp,
                nonce,
                signature,
                orchestratorId,
                body
        );
        HmacAuth.AuthResult result = HmacAuth.verify(
                authData,
                config.registry.auth.sharedKey,
                config.registry.auth.nonceBytes != null ? config.registry.auth.nonceBytes : 0,
                config.registry.auth.clockSkewSeconds != null ? config.registry.auth.clockSkewSeconds : 0,
                nonceCache
        );
        if (!result.ok) {
            writeResponse(exchange, 401, RegistryResponse.error(result.message));
            return false;
        }
        return true;
    }

    private boolean isAllowedRemote(HttpExchange exchange) {
        InetAddress address = exchange.getRemoteAddress().getAddress();
        if (!networkMatcher.isAllowed(address)) {
            return false;
        }
        return true;
    }

    private void validateOrchestrator(String orchestratorId, HttpExchange exchange) {
        requireNonBlank(orchestratorId, "orchestratorId");
        if (config.registry.allowlist == null) {
            throw new IllegalArgumentException("registry allowlist missing");
        }
        InetAddress address = exchange.getRemoteAddress().getAddress();
        boolean matched = false;
        for (HyproxConfig.RegistryAllowlistEntry entry : config.registry.allowlist) {
            if (entry == null) {
                continue;
            }
            if (!orchestratorId.equals(entry.orchestratorId)) {
                continue;
            }
            if (entry.address == null || entry.address.equals(address.getHostAddress())) {
                matched = true;
            }
        }
        if (!matched) {
            throw new IllegalArgumentException("orchestrator not allowlisted");
        }
    }

    private void validatePool(String pool, String orchestratorId) {
        requireNonBlank(pool, "pool");
        if (config.routing == null || config.routing.pools == null || !config.routing.pools.containsKey(pool)) {
            throw new IllegalArgumentException("pool not found: " + pool);
        }
        if (config.registry.allowlist == null) {
            throw new IllegalArgumentException("registry allowlist missing");
        }
        boolean allowed = false;
        for (HyproxConfig.RegistryAllowlistEntry entry : config.registry.allowlist) {
            if (entry == null) {
                continue;
            }
            if (!orchestratorId.equals(entry.orchestratorId)) {
                continue;
            }
            if (entry.allowedPools != null && entry.allowedPools.contains(pool)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new IllegalArgumentException("pool not allowed for orchestrator");
        }
    }

    private void validateBackendId(String backendId, String orchestratorId) {
        requireNonBlank(backendId, "backendId");
        if (config.registry.allowlist == null) {
            throw new IllegalArgumentException("registry allowlist missing");
        }
        for (HyproxConfig.RegistryAllowlistEntry entry : config.registry.allowlist) {
            if (entry == null) {
                continue;
            }
            if (!orchestratorId.equals(entry.orchestratorId)) {
                continue;
            }
            if (entry.allowedBackendIdPrefixes != null) {
                for (String prefix : entry.allowedBackendIdPrefixes) {
                    if (backendId.startsWith(prefix)) {
                        return;
                    }
                }
            }
        }
        throw new IllegalArgumentException("backendId not allowed by orchestrator prefix rules");
    }

    private void validateBackendHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("host is required");
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            if (!networkMatcher.isAllowed(address)) {
                throw new IllegalArgumentException("backend host not allowed");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid backend host");
        }
    }

    private int requirePort(Integer port, String field) {
        if (port == null || port < 1 || port > 65535) {
            throw new IllegalArgumentException(field + " must be between 1 and 65535");
        }
        return port;
    }

    private void requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private String rawPath(URI uri) {
        String path = uri.getRawPath();
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return path;
        }
        return path + "?" + query;
    }

    private List<Map<String, Object>> toView(List<RegisteredBackend> backends) {
        List<Map<String, Object>> view = new java.util.ArrayList<>();
        for (RegisteredBackend backend : backends) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", backend.id());
            item.put("pool", backend.pool());
            item.put("host", backend.host());
            item.put("port", backend.port());
            item.put("weight", backend.weight());
            item.put("maxPlayers", backend.maxPlayers());
            item.put("tags", backend.tags());
            item.put("orchestratorId", backend.orchestratorId());
            item.put("lastHeartbeat", backend.lastHeartbeat().toString());
            item.put("expiresAt", backend.expiresAt().toString());
            item.put("draining", backend.draining());
            view.add(item);
        }
        return view;
    }
}
