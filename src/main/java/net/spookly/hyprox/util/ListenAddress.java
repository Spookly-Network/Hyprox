package net.spookly.hyprox.util;

import java.net.InetSocketAddress;

public final class ListenAddress {
    private final String host;
    private final int port;

    private ListenAddress(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static ListenAddress parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("listen address is required");
        }
        String value = raw.trim();
        int lastColon = value.lastIndexOf(':');
        if (lastColon <= 0 || lastColon == value.length() - 1) {
            throw new IllegalArgumentException("listen address must be host:port: " + raw);
        }
        String host = value.substring(0, lastColon).trim();
        String portRaw = value.substring(lastColon + 1).trim();
        int port;
        try {
            port = Integer.parseInt(portRaw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("listen port must be numeric: " + portRaw, e);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("listen port must be between 1 and 65535: " + port);
        }
        if (host.isEmpty()) {
            throw new IllegalArgumentException("listen host is required");
        }
        return new ListenAddress(host, port);
    }

    public InetSocketAddress toSocketAddress() {
        return new InetSocketAddress(host, port);
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }
}
