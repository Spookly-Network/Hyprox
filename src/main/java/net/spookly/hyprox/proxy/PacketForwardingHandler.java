package net.spookly.hyprox.proxy;

import java.util.Objects;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.auth.AuthToken;
import com.hypixel.hytale.protocol.packets.auth.ServerAuthToken;
import com.hypixel.hytale.protocol.packets.connection.Disconnect;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;

/**
 * Forwards decoded packets between paired streams.
 */
public final class PacketForwardingHandler extends SimpleChannelInboundHandler<Packet> {
    private final Channel outboundChannel;
    private final ProxyBridgeSession session;
    private final ProxyDataPathMetrics metrics;
    private final ForwardDirection direction;
    private final ProxyAuthSession authSession;
    private final boolean captureTokens;

    public PacketForwardingHandler(Channel outboundChannel,
                                   ProxyBridgeSession session,
                                   ProxyDataPathMetrics metrics,
                                   ForwardDirection direction,
                                   ProxyAuthSession authSession,
                                   boolean captureTokens) {
        this.outboundChannel = Objects.requireNonNull(outboundChannel, "outboundChannel");
        this.session = Objects.requireNonNull(session, "session");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.authSession = authSession;
        this.captureTokens = captureTokens;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg) {
        if (!outboundChannel.isActive()) {
            session.close();
            return;
        }
        captureAuthPacket(msg);
        if (msg instanceof Disconnect) {
            forwardDisconnect(msg);
            return;
        }
        recordPacket();
        ReferenceCountUtil.retain(msg);
        outboundChannel.writeAndFlush(msg).addListener(future -> {
            if (!future.isSuccess()) {
                session.close();
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        session.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        session.close();
    }

    private void forwardDisconnect(Packet disconnect) {
        ReferenceCountUtil.retain(disconnect);
        outboundChannel.writeAndFlush(disconnect).addListener(future -> session.close());
    }

    private void recordPacket() {
        if (direction == ForwardDirection.CLIENT_TO_BACKEND) {
            metrics.recordClientToBackendPacket();
        } else {
            metrics.recordBackendToClientPacket();
        }
    }

    private void captureAuthPacket(Packet msg) {
        if (!captureTokens || authSession == null) {
            return;
        }
        if (msg instanceof AuthToken) {
            AuthToken authToken = (AuthToken) msg;
            authSession.captureAuthToken(authToken.accessToken, authToken.serverAuthorizationGrant);
            return;
        }
        if (msg instanceof ServerAuthToken) {
            ServerAuthToken serverAuthToken = (ServerAuthToken) msg;
            authSession.captureServerAccessToken(serverAuthToken.serverAccessToken);
        }
    }

    public enum ForwardDirection {
        CLIENT_TO_BACKEND,
        BACKEND_TO_CLIENT
    }
}
