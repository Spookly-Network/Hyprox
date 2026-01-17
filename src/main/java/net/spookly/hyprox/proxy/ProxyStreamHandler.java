package net.spookly.hyprox.proxy;

import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import com.hypixel.hytale.protocol.HostAddress;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.io.netty.PacketDecoder;
import com.hypixel.hytale.protocol.io.netty.PacketEncoder;
import com.hypixel.hytale.protocol.io.netty.ProtocolUtil;
import com.hypixel.hytale.protocol.packets.auth.AuthToken;
import com.hypixel.hytale.protocol.packets.auth.ClientReferral;
import com.hypixel.hytale.protocol.packets.connection.ClientType;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.protocol.packets.connection.Disconnect;
import com.hypixel.hytale.protocol.packets.connection.DisconnectType;
import com.hypixel.hytale.server.core.io.netty.PacketArrayEncoder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import net.spookly.hyprox.auth.ReferralService;
import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.routing.BackendReservation;
import net.spookly.hyprox.routing.BackendTarget;
import net.spookly.hyprox.routing.DataPath;
import net.spookly.hyprox.routing.RoutingDecision;
import net.spookly.hyprox.routing.RoutingPlanner;
import net.spookly.hyprox.routing.RoutingRequest;

/**
 * Handles the initial client handshake and routes to referral or full proxy paths.
 */
public final class ProxyStreamHandler extends SimpleChannelInboundHandler<Packet> {
    private static final int MAX_PROTOCOL_HASH_LENGTH = 64;
    private static final String EXPECTED_PROTOCOL_HASH =
            "6708f121966c1c443f4b0eb525b2f81d0a8dc61f5003a692a8fa157e5e02cea9";

    private final HyproxConfig config;
    private final RoutingPlanner routingPlanner;
    private final ProxySessionLimiter sessionLimiter;
    private final ReferralService referralService;
    private final BackendConnector backendConnector;
    private boolean handled;
    private boolean sessionTracked;
    private String remoteAddress;
    private BackendReservation backendReservation;
    private ProxyBridgeSession bridgeSession;
    private ProxyDataPathMetrics dataPathMetrics;
    private boolean forwardingEnabled;
    private boolean bufferingEnabled;
    private final Deque<Packet> pendingPackets = new ArrayDeque<>();
    private ProxyAuthSession authSession;

    public ProxyStreamHandler(HyproxConfig config,
                              RoutingPlanner routingPlanner,
                              ProxySessionLimiter sessionLimiter,
                              ReferralService referralService,
                              BackendConnector backendConnector) {
        this.config = Objects.requireNonNull(config, "config");
        this.routingPlanner = Objects.requireNonNull(routingPlanner, "routingPlanner");
        this.sessionLimiter = Objects.requireNonNull(sessionLimiter, "sessionLimiter");
        this.referralService = Objects.requireNonNull(referralService, "referralService");
        this.backendConnector = Objects.requireNonNull(backendConnector, "backendConnector");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg) {
        if (handled) {
            if (forwardingEnabled) {
                ReferenceCountUtil.retain(msg);
                ctx.fireChannelRead(msg);
            } else if (bufferingEnabled) {
                queuePendingPacket(msg);
            }
            return;
        }
        if (!(msg instanceof Connect)) {
            sendDisconnect(ctx, "unexpected packet", DisconnectType.Disconnect);
            return;
        }
        handled = true;
        clearHandshakeTimeout(ctx);
        if (remoteAddress != null && !sessionLimiter.tryAcquireHandshake(remoteAddress)) {
            sendDisconnect(ctx, "rate limited", DisconnectType.Disconnect);
            return;
        }
        Connect connect = (Connect) msg;
        if (!validateProtocolHash(ctx, connect)) {
            return;
        }
        authSession = ensureAuthSession(ctx);
        if (authSession != null) {
            authSession.captureIdentityToken(connect.identityToken);
        }
        RoutingDecision decision = routingPlanner.decide(toRequest(ctx, connect));
        storeRoutingContext(ctx, decision);
        backendReservation = decision.reservation();
        BackendTarget backend = decision.backend();
        if (backend == null) {
            String reason = decision.reason() == null ? "no backend available" : "routing failed: " + decision.reason();
            sendDisconnect(ctx, reason, DisconnectType.Disconnect);
            return;
        }
        if (decision.dataPath() == DataPath.FULL_PROXY) {
            startFullProxy(ctx, backend, connect);
            return;
        }
        sendReferral(ctx, backend, connect);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof ReadTimeoutException) {
            sendDisconnect(ctx, "handshake timeout", DisconnectType.Disconnect);
            return;
        }
        if (bridgeSession != null) {
            bridgeSession.close();
        }
        clearPendingPackets();
        ProtocolUtil.closeApplicationConnection(ctx.channel());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        remoteAddress = resolveRemoteAddress(ctx);
        if (remoteAddress != null && !sessionLimiter.tryOpenSession(remoteAddress)) {
            sendDisconnect(ctx, "too many sessions", DisconnectType.Disconnect);
            return;
        }
        sessionTracked = remoteAddress != null;
        ensureSessionContext(ctx);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (sessionTracked) {
            sessionLimiter.releaseSession(remoteAddress);
        }
        if (bridgeSession != null) {
            bridgeSession.close();
        }
        clearPendingPackets();
        clearAuthSession(ctx);
        releaseReservation();
        ctx.fireChannelInactive();
    }

    private RoutingRequest toRequest(ChannelHandlerContext ctx, Connect connect) {
        String clientType = mapClientType(connect.clientType);
        ReferralService.VerifyResult referralResult = referralService.verifyReferral(connect.referralData, connect.uuid);
        String referralSource = null;
        String targetBackendId = null;
        if (referralResult.ok()) {
            if (connect.referralSource != null
                    && connect.referralSource.host != null
                    && !connect.referralSource.host.trim().isEmpty()) {
                referralSource = connect.referralSource.host.trim();
            }
            targetBackendId = referralResult.targetBackendId();
        }
        String selectionKey = resolveSelectionKey(ctx);
        return new RoutingRequest(clientType, referralSource, selectionKey, targetBackendId);
    }

    private String mapClientType(ClientType clientType) {
        if (clientType == null) {
            return null;
        }
        return clientType.name().toLowerCase(Locale.ROOT);
    }

    private void sendReferral(ChannelHandlerContext ctx, BackendTarget backend, Connect connect) {
        if (backend.port() <= 0 || backend.port() > 65535) {
            releaseReservation();
            sendDisconnect(ctx, "invalid backend port", DisconnectType.Disconnect);
            return;
        }
        ReferralService.SignResult signResult = referralService.signReferral(backend, connect.uuid);
        if (!signResult.ok()) {
            releaseReservation();
            sendDisconnect(ctx, "referral signing failed", DisconnectType.Disconnect);
            return;
        }
        HostAddress hostAddress = new HostAddress(backend.host(), (short) backend.port());
        ClientReferral referral = new ClientReferral(hostAddress, signResult.payload());
        ctx.writeAndFlush(referral).addListener(ProtocolUtil.CLOSE_ON_COMPLETE);
    }

    private void sendDisconnect(ChannelHandlerContext ctx, String reason, DisconnectType type) {
        Disconnect disconnect = new Disconnect(reason, type);
        ctx.writeAndFlush(disconnect).addListener(ProtocolUtil.CLOSE_ON_COMPLETE);
    }

    private void sendDisconnect(Channel channel, String reason, DisconnectType type) {
        Disconnect disconnect = new Disconnect(reason, type);
        channel.writeAndFlush(disconnect).addListener(ProtocolUtil.CLOSE_ON_COMPLETE);
    }

    private void startFullProxy(ChannelHandlerContext ctx, BackendTarget backend, Connect connect) {
        if (backend.port() <= 0 || backend.port() > 65535) {
            releaseReservation();
            sendDisconnect(ctx, "invalid backend port", DisconnectType.Disconnect);
            return;
        }
        Channel clientChannel = ctx.channel();
        clientChannel.config().setAutoRead(false);
        bufferingEnabled = true;
        long startNanos = System.nanoTime();
        Future<BackendConnection> future = backendConnector.connect(backend);
        future.addListener(connectFuture -> {
            Channel channel = ctx.channel();
            if (!connectFuture.isSuccess()) {
                channel.eventLoop().execute(() -> handleBackendConnectFailure(channel));
                return;
            }
            BackendConnection connection = (BackendConnection) connectFuture.getNow();
            long latencyNanos = System.nanoTime() - startNanos;
            channel.eventLoop().execute(() -> attachFullProxy(ctx, connection, connect, latencyNanos));
        });
    }

    private void handleBackendConnectFailure(Channel channel) {
        if (!channel.isActive()) {
            bufferingEnabled = false;
            clearPendingPackets();
            releaseReservation();
            return;
        }
        bufferingEnabled = false;
        clearPendingPackets();
        releaseReservation();
        sendDisconnect(channel, "backend connection failed", DisconnectType.Disconnect);
    }

    private void attachFullProxy(ChannelHandlerContext ctx, BackendConnection connection, Connect connect, long latencyNanos) {
        Channel clientChannel = ctx.channel();
        if (!clientChannel.isActive()) {
            bufferingEnabled = false;
            clearPendingPackets();
            connection.close();
            releaseReservation();
            return;
        }
        dataPathMetrics = new ProxyDataPathMetrics();
        dataPathMetrics.recordBackendConnectLatencyNanos(latencyNanos);
        bridgeSession = new ProxyBridgeSession(clientChannel, connection, backendReservation);
        forwardingEnabled = true;
        bufferingEnabled = false;
        addTrafficMetrics(clientChannel);
        setupBackendPipeline(connection, clientChannel);
        addClientForwarder(ctx, connection);
        installBackpressure(clientChannel, connection.streamChannel());
        connection.streamChannel().writeAndFlush(connect).addListener(future -> {
            if (!future.isSuccess()) {
                bridgeSession.close();
                clearPendingPackets();
                return;
            }
            flushPendingPackets(connection.streamChannel());
        });
        clientChannel.config().setAutoRead(true);
    }

    private void addTrafficMetrics(Channel clientChannel) {
        ChannelPipeline pipeline = clientChannel.pipeline();
        if (pipeline.get("trafficMetrics") == null && dataPathMetrics != null) {
            pipeline.addFirst("trafficMetrics", new TrafficMetricsHandler(dataPathMetrics, TrafficMetricsHandler.TrafficSide.CLIENT_STREAM));
        }
    }

    private void setupBackendPipeline(BackendConnection connection, Channel clientChannel) {
        ChannelPipeline pipeline = connection.streamChannel().pipeline();
        if (pipeline.get("packetDecoder") == null) {
            pipeline.addLast("packetDecoder", new PacketDecoder());
        }
        if (pipeline.get("packetEncoder") == null) {
            pipeline.addLast("packetEncoder", new PacketEncoder());
        }
        if (pipeline.get("packetArrayEncoder") == null) {
            pipeline.addLast("packetArrayEncoder", new PacketArrayEncoder());
        }
        if (pipeline.get("backendForwarder") == null) {
            pipeline.addLast("backendForwarder", new PacketForwardingHandler(
                    clientChannel,
                    bridgeSession,
                    dataPathMetrics,
                    PacketForwardingHandler.ForwardDirection.BACKEND_TO_CLIENT,
                    authSession,
                    isTerminateAuth()
            ));
        }
    }

    private void addClientForwarder(ChannelHandlerContext ctx, BackendConnection connection) {
        ChannelPipeline pipeline = ctx.channel().pipeline();
        if (pipeline.get("clientForwarder") == null) {
            pipeline.addLast("clientForwarder", new PacketForwardingHandler(
                    connection.streamChannel(),
                    bridgeSession,
                    dataPathMetrics,
                    PacketForwardingHandler.ForwardDirection.CLIENT_TO_BACKEND,
                    authSession,
                    isTerminateAuth()
            ));
        }
    }

    private void installBackpressure(Channel clientChannel, Channel backendChannel) {
        if (backendChannel.pipeline().get("clientBackpressure") == null) {
            backendChannel.pipeline().addLast("clientBackpressure", new BackpressureRelayHandler(clientChannel));
        }
        if (clientChannel.pipeline().get("backendBackpressure") == null) {
            clientChannel.pipeline().addLast("backendBackpressure", new BackpressureRelayHandler(backendChannel));
        }
    }

    private void queuePendingPacket(Packet packet) {
        captureAuthPacket(packet);
        ReferenceCountUtil.retain(packet);
        pendingPackets.add(packet);
    }

    private void flushPendingPackets(Channel backendChannel) {
        while (!pendingPackets.isEmpty()) {
            Packet packet = pendingPackets.poll();
            backendChannel.write(packet).addListener(future -> {
                ReferenceCountUtil.release(packet);
                if (!future.isSuccess() && bridgeSession != null) {
                    bridgeSession.close();
                }
            });
        }
        backendChannel.flush();
    }

    private void clearPendingPackets() {
        while (!pendingPackets.isEmpty()) {
            ReferenceCountUtil.release(pendingPackets.poll());
        }
    }

    private void ensureSessionContext(ChannelHandlerContext ctx) {
        Channel contextChannel = resolveContextChannel(ctx);
        if (contextChannel == null || contextChannel.attr(ProxySessionContext.SESSION_CONTEXT).get() != null) {
            return;
        }
        X509Certificate certificate = resolveClientCertificate(contextChannel);
        ProxySessionContext context = ProxySessionContext.from(remoteAddress, certificate);
        contextChannel.attr(ProxySessionContext.SESSION_CONTEXT).set(context);
    }

    private String resolveSelectionKey(ChannelHandlerContext ctx) {
        Channel contextChannel = resolveContextChannel(ctx);
        if (contextChannel != null) {
            ProxySessionContext context = contextChannel.attr(ProxySessionContext.SESSION_CONTEXT).get();
            if (context != null) {
                if (!isBlank(context.clientCertificateSha256())) {
                    return context.clientCertificateSha256();
                }
                if (!isBlank(context.remoteAddress())) {
                    return context.remoteAddress();
                }
            }
        }
        return isBlank(remoteAddress) ? null : remoteAddress;
    }

    private X509Certificate resolveClientCertificate(Channel contextChannel) {
        Channel channel = contextChannel;
        if (!(channel instanceof QuicChannel) && channel.parent() != null) {
            channel = channel.parent();
        }
        if (!(channel instanceof QuicChannel)) {
            return null;
        }
        SSLEngine sslEngine = ((QuicChannel) channel).sslEngine();
        if (sslEngine == null) {
            return null;
        }
        SSLSession session = sslEngine.getSession();
        if (session == null) {
            return null;
        }
        try {
            Certificate[] certificates = session.getPeerCertificates();
            if (certificates == null || certificates.length == 0) {
                return null;
            }
            for (Certificate certificate : certificates) {
                if (certificate instanceof X509Certificate) {
                    return (X509Certificate) certificate;
                }
            }
        } catch (SSLPeerUnverifiedException ignored) {
            return null;
        }
        return null;
    }

    private String resolveRemoteAddress(ChannelHandlerContext ctx) {
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress) {
            InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
            if (address.getAddress() != null) {
                return address.getAddress().getHostAddress();
            }
            return address.getHostString();
        }
        return null;
    }

    private void clearHandshakeTimeout(ChannelHandlerContext ctx) {
        if (ctx.pipeline().get("handshakeTimeout") != null) {
            ctx.pipeline().remove("handshakeTimeout");
        }
    }

    private void storeRoutingContext(ChannelHandlerContext ctx, RoutingDecision decision) {
        Channel contextChannel = resolveContextChannel(ctx);
        if (contextChannel == null) {
            return;
        }
        contextChannel.attr(RoutingDecisionContext.ROUTING_CONTEXT).set(RoutingDecisionContext.from(decision));
    }

    private Channel resolveContextChannel(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        return channel.parent() == null ? channel : channel.parent();
    }

    private void releaseReservation() {
        if (backendReservation != null) {
            backendReservation.release();
            backendReservation = null;
        }
    }

    private boolean validateProtocolHash(ChannelHandlerContext ctx, Connect connect) {
        String protocolHash = connect.protocolHash;
        if (protocolHash == null || protocolHash.trim().isEmpty()) {
            sendDisconnect(ctx, "missing protocol hash", DisconnectType.Disconnect);
            return false;
        }
        if (protocolHash.length() > MAX_PROTOCOL_HASH_LENGTH) {
            sendDisconnect(ctx, "protocol hash too long", DisconnectType.Disconnect);
            return false;
        }
        if (!EXPECTED_PROTOCOL_HASH.equals(protocolHash)) {
            sendDisconnect(ctx, "protocol hash mismatch", DisconnectType.Disconnect);
            return false;
        }
        return true;
    }

    private ProxyAuthSession ensureAuthSession(ChannelHandlerContext ctx) {
        if (!isTerminateAuth()) {
            return null;
        }
        Channel contextChannel = resolveContextChannel(ctx);
        if (contextChannel == null) {
            return null;
        }
        ProxyAuthSession session = contextChannel.attr(ProxyAuthSession.AUTH_SESSION).get();
        if (session == null) {
            session = new ProxyAuthSession();
            contextChannel.attr(ProxyAuthSession.AUTH_SESSION).set(session);
        }
        return session;
    }

    private void clearAuthSession(ChannelHandlerContext ctx) {
        Channel contextChannel = resolveContextChannel(ctx);
        if (contextChannel == null) {
            return;
        }
        ProxyAuthSession session = contextChannel.attr(ProxyAuthSession.AUTH_SESSION).get();
        if (session != null) {
            session.clearSensitive();
        }
        contextChannel.attr(ProxyAuthSession.AUTH_SESSION).set(null);
        authSession = null;
    }

    private void captureAuthPacket(Packet packet) {
        if (!isTerminateAuth() || authSession == null) {
            return;
        }
        if (packet instanceof AuthToken) {
            AuthToken authToken = (AuthToken) packet;
            authSession.captureAuthToken(authToken.accessToken, authToken.serverAuthorizationGrant);
        }
    }

    private boolean isTerminateAuth() {
        return config.auth != null && "terminate".equalsIgnoreCase(config.auth.mode);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
