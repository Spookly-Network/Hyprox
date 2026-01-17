package net.spookly.hyprox.proxy;

import com.hypixel.hytale.protocol.io.netty.PacketDecoder;
import com.hypixel.hytale.protocol.io.netty.PacketEncoder;
import com.hypixel.hytale.server.core.io.netty.PacketArrayEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.spookly.hyprox.auth.ReferralService;
import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.routing.RoutingPlanner;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Initializes QUIC stream pipelines for proxy handshakes.
 */
public final class ProxyStreamInitializer extends ChannelInitializer<QuicStreamChannel> {
    private final HyproxConfig config;
    private final RoutingPlanner routingPlanner;
    private final ProxySessionLimiter sessionLimiter;
    private final ReferralService referralService;
    private final BackendConnector backendConnector;

    public ProxyStreamInitializer(HyproxConfig config,
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
    protected void initChannel(QuicStreamChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        if (config.proxy != null && config.proxy.timeouts != null && config.proxy.timeouts.handshakeMs != null) {
            pipeline.addLast("handshakeTimeout", new ReadTimeoutHandler(config.proxy.timeouts.handshakeMs, TimeUnit.MILLISECONDS));
        }
        pipeline.addLast("packetDecoder", new PacketDecoder());
        pipeline.addLast("packetEncoder", new PacketEncoder());
        pipeline.addLast("packetArrayEncoder", new PacketArrayEncoder());
        pipeline.addLast("handler", new ProxyStreamHandler(config, routingPlanner, sessionLimiter, referralService, backendConnector));
    }
}
