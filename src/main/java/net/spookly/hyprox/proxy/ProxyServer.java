package net.spookly.hyprox.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.ssl.ClientAuth;
import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.routing.RoutingPlanner;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * QUIC proxy listener that accepts client sessions and routes them to backends.
 */
public final class ProxyServer {
    private final HyproxConfig config;
    private final RoutingPlanner routingPlanner;
    private final ProxySessionLimiter sessionLimiter;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public ProxyServer(HyproxConfig config, RoutingPlanner routingPlanner) {
        this.config = Objects.requireNonNull(config, "config");
        this.routingPlanner = Objects.requireNonNull(routingPlanner, "routingPlanner");
        HyproxConfig.LimitsConfig limits = config.proxy == null ? null : config.proxy.limits;
        this.sessionLimiter = new ProxySessionLimiter(
                limits == null ? null : limits.handshakesPerMinutePerIp,
                limits == null ? null : limits.concurrentPerIp,
                null
        );
    }

    /**
     * Start the QUIC listener for incoming client connections.
     */
    public void start() {
        if (channel != null) {
            return;
        }
        HyproxConfig.ProxyConfig proxy = config.proxy;
        HyproxConfig.QuicConfig quic = proxy.quic;
        QuicSslContext sslContext = buildSslContext(quic);
        QuicServerCodecBuilder codecBuilder = new QuicServerCodecBuilder()
                .sslContext(sslContext)
                .streamHandler(new ProxyStreamInitializer(config, routingPlanner, sessionLimiter));

        if (proxy.timeouts != null && proxy.timeouts.idleMs != null) {
            codecBuilder.maxIdleTimeout(proxy.timeouts.idleMs, TimeUnit.MILLISECONDS);
        }
        if (quic.maxBidirectionalStreams != null && quic.maxBidirectionalStreams > 0) {
            codecBuilder.initialMaxStreamsBidirectional(quic.maxBidirectionalStreams);
        }
        if (quic.maxUnidirectionalStreams != null && quic.maxUnidirectionalStreams > 0) {
            codecBuilder.initialMaxStreamsUnidirectional(quic.maxUnidirectionalStreams);
        }
        if (quic.mtu != null && quic.mtu > 0) {
            codecBuilder.maxRecvUdpPayloadSize(quic.mtu);
            codecBuilder.maxSendUdpPayloadSize(quic.mtu);
        }

        workerGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .handler(codecBuilder.build());

        InetSocketAddress address = new InetSocketAddress(proxy.listen.host, proxy.listen.port);
        try {
            channel = bootstrap.bind(address).sync().channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Proxy bind interrupted", e);
        }
        System.out.println("Proxy listening on " + address.getHostString() + ":" + address.getPort());
    }

    /**
     * Stop the QUIC listener and event loops.
     */
    public void stop() {
        if (channel != null) {
            channel.close();
            channel = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
    }

    private QuicSslContext buildSslContext(HyproxConfig.QuicConfig quic) {
        File certFile = new File(quic.cert);
        File keyFile = new File(quic.key);
        QuicSslContextBuilder builder = QuicSslContextBuilder.forServer(certFile, null, keyFile);
        List<String> alpn = quic.alpn;
        if (alpn != null && !alpn.isEmpty()) {
            builder.applicationProtocols(alpn.toArray(new String[0]));
        }
        ClientAuth clientAuth = ClientAuth.NONE;
        boolean requireClientCert = Boolean.TRUE.equals(quic.requireClientCert);
        if (requireClientCert) {
            clientAuth = ClientAuth.REQUIRE;
        } else if (quic.clientCa != null && !quic.clientCa.trim().isEmpty()) {
            clientAuth = ClientAuth.OPTIONAL;
        }
        builder.clientAuth(clientAuth);
        if (quic.clientCa != null && !quic.clientCa.trim().isEmpty()) {
            builder.trustManager(new File(quic.clientCa));
        }
        return builder.build();
    }
}
