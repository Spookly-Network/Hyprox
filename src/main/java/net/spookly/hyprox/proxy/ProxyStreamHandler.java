package net.spookly.hyprox.proxy;

import com.hypixel.hytale.protocol.HostAddress;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.io.netty.ProtocolUtil;
import com.hypixel.hytale.protocol.packets.auth.ClientReferral;
import com.hypixel.hytale.protocol.packets.connection.ClientType;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.protocol.packets.connection.Disconnect;
import com.hypixel.hytale.protocol.packets.connection.DisconnectType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.routing.BackendTarget;
import net.spookly.hyprox.routing.DataPath;
import net.spookly.hyprox.routing.RoutingDecision;
import net.spookly.hyprox.routing.RoutingPlanner;
import net.spookly.hyprox.routing.RoutingRequest;

import java.util.Locale;
import java.util.Objects;

/**
 * Handles the initial client handshake and issues referral redirects.
 */
public final class ProxyStreamHandler extends SimpleChannelInboundHandler<Packet> {
    private final RoutingPlanner routingPlanner;
    private boolean handled;

    public ProxyStreamHandler(HyproxConfig config, RoutingPlanner routingPlanner) {
        Objects.requireNonNull(config, "config");
        this.routingPlanner = Objects.requireNonNull(routingPlanner, "routingPlanner");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg) {
        if (handled) {
            return;
        }
        if (!(msg instanceof Connect)) {
            sendDisconnect(ctx, "unexpected packet", DisconnectType.Disconnect);
            return;
        }
        handled = true;
        Connect connect = (Connect) msg;
        RoutingDecision decision = routingPlanner.decide(toRequest(connect));
        BackendTarget backend = decision.backend();
        if (backend == null) {
            String reason = decision.reason() == null ? "no backend available" : "routing failed: " + decision.reason();
            sendDisconnect(ctx, reason, DisconnectType.Disconnect);
            return;
        }
        if (decision.dataPath() == DataPath.FULL_PROXY) {
            sendDisconnect(ctx, "full proxy path not yet enabled", DisconnectType.Disconnect);
            return;
        }
        sendReferral(ctx, backend);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ProtocolUtil.closeApplicationConnection(ctx.channel());
    }

    private RoutingRequest toRequest(Connect connect) {
        String clientType = mapClientType(connect.clientType);
        String referralSource = null;
        if (connect.referralData == null || connect.referralData.length == 0) {
            if (connect.referralSource != null
                    && connect.referralSource.host != null
                    && !connect.referralSource.host.trim().isEmpty()) {
                referralSource = connect.referralSource.host.trim();
            }
        }
        return new RoutingRequest(clientType, referralSource);
    }

    private String mapClientType(ClientType clientType) {
        if (clientType == null) {
            return null;
        }
        return clientType.name().toLowerCase(Locale.ROOT);
    }

    private void sendReferral(ChannelHandlerContext ctx, BackendTarget backend) {
        if (backend.port() <= 0 || backend.port() > 65535) {
            sendDisconnect(ctx, "invalid backend port", DisconnectType.Disconnect);
            return;
        }
        HostAddress hostAddress = new HostAddress(backend.host(), (short) backend.port());
        ClientReferral referral = new ClientReferral(hostAddress, new byte[0]);
        ctx.writeAndFlush(referral).addListener(ProtocolUtil.CLOSE_ON_COMPLETE);
    }

    private void sendDisconnect(ChannelHandlerContext ctx, String reason, DisconnectType type) {
        Disconnect disconnect = new Disconnect(reason, type);
        ctx.writeAndFlush(disconnect).addListener(ProtocolUtil.CLOSE_ON_COMPLETE);
    }
}
