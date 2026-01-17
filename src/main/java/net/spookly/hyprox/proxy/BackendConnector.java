package net.spookly.hyprox.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicChannelBootstrap;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.routing.BackendTarget;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Establishes QUIC connections from the proxy to backend servers.
 */
public final class BackendConnector {
    private final HyproxConfig config;
    private final EventLoopGroup workerGroup;
    private final QuicSslContext sslContext;

    public BackendConnector(HyproxConfig config, EventLoopGroup workerGroup) {
        this.config = Objects.requireNonNull(config, "config");
        this.workerGroup = Objects.requireNonNull(workerGroup, "workerGroup");
        this.sslContext = buildSslContext(config.proxy == null ? null : config.proxy.quic);
    }

    public BackendConnector(HyproxConfig config) {
        this(config, new NioEventLoopGroup());
    }

    public EventLoopGroup workerGroup() {
        return workerGroup;
    }

    public Future<BackendConnection> connect(BackendTarget backend) {
        Objects.requireNonNull(backend, "backend");
        Promise<BackendConnection> promise = workerGroup.next().newPromise();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .handler(buildClientCodec());

        ChannelFuture bindFuture = bootstrap.bind(0);
        bindFuture.addListener(future -> {
            if (!future.isSuccess()) {
                promise.setFailure(future.cause());
                return;
            }
            Channel datagramChannel = (Channel) future.getNow();
            InetSocketAddress remoteAddress = new InetSocketAddress(backend.host(), backend.port());
            QuicChannelBootstrap quicBootstrap = QuicChannel.newBootstrap(datagramChannel)
                    .handler(new BackendQuicChannelHandler())
                    .streamHandler(new BackendStreamHandler())
                    .remoteAddress(remoteAddress);
            quicBootstrap.connect().addListener(connectFuture -> {
                if (!connectFuture.isSuccess()) {
                    datagramChannel.close();
                    promise.setFailure(connectFuture.cause());
                    return;
                }
                QuicChannel quicChannel = (QuicChannel) connectFuture.getNow();
                BackendIdentityVerifier.VerificationResult result = BackendIdentityVerifier.verify(
                        quicChannel,
                        backend.host(),
                        config.proxy == null || config.proxy.quic == null ? null : config.proxy.quic.backendSanAllowlist
                );
                if (!result.isOk()) {
                    quicChannel.close();
                    datagramChannel.close();
                    promise.setFailure(new IllegalStateException(result.reason()));
                    return;
                }
                quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new BackendStreamHandler())
                        .addListener(streamFuture -> {
                            if (!streamFuture.isSuccess()) {
                                quicChannel.close();
                                datagramChannel.close();
                                promise.setFailure(streamFuture.cause());
                                return;
                            }
                            QuicStreamChannel streamChannel = (QuicStreamChannel) streamFuture.getNow();
                            promise.setSuccess(new BackendConnection(backend, datagramChannel, quicChannel, streamChannel));
                        });
            });
        });
        return promise;
    }

    private QuicSslContext buildSslContext(HyproxConfig.QuicConfig quic) {
        if (quic == null) {
            throw new IllegalStateException("proxy.quic config is required");
        }
        QuicSslContextBuilder builder = QuicSslContextBuilder.forClient();
        if (quic.backendCa != null && !quic.backendCa.trim().isEmpty()) {
            builder.trustManager(new File(quic.backendCa));
        }
        if (quic.cert != null && quic.key != null) {
            builder.keyManager(new File(quic.cert), null, new File(quic.key));
        }
        List<String> alpn = quic.alpn;
        if (alpn != null && !alpn.isEmpty()) {
            builder.applicationProtocols(alpn.toArray(new String[0]));
        }
        return builder.build();
    }

    private ChannelHandler buildClientCodec() {
        HyproxConfig.ProxyConfig proxy = config.proxy;
        HyproxConfig.QuicConfig quic = proxy == null ? null : proxy.quic;
        QuicClientCodecBuilder builder = new QuicClientCodecBuilder().sslContext(sslContext);
        if (proxy != null && proxy.timeouts != null && proxy.timeouts.idleMs != null) {
            builder.maxIdleTimeout(proxy.timeouts.idleMs, TimeUnit.MILLISECONDS);
        }
        if (quic != null) {
            if (quic.maxBidirectionalStreams != null && quic.maxBidirectionalStreams > 0) {
                builder.initialMaxStreamsBidirectional(quic.maxBidirectionalStreams);
            }
            if (quic.maxUnidirectionalStreams != null && quic.maxUnidirectionalStreams > 0) {
                builder.initialMaxStreamsUnidirectional(quic.maxUnidirectionalStreams);
            }
            if (quic.mtu != null && quic.mtu > 0) {
                builder.maxRecvUdpPayloadSize(quic.mtu);
                builder.maxSendUdpPayloadSize(quic.mtu);
            }
        }
        return builder.build();
    }

    private static final class BackendQuicChannelHandler extends io.netty.channel.ChannelInboundHandlerAdapter {
    }

    private static final class BackendStreamHandler extends io.netty.channel.ChannelInboundHandlerAdapter {
    }
}
