package net.spookly.hyprox.proxy;

import com.hypixel.hytale.protocol.HostAddress;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.io.netty.ProtocolUtil;
import com.hypixel.hytale.protocol.packets.auth.ClientReferral;
import com.hypixel.hytale.protocol.packets.connection.ClientType;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.protocol.packets.connection.Disconnect;
import com.hypixel.hytale.protocol.packets.connection.DisconnectType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.timeout.ReadTimeoutException;
import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.routing.BackendTarget;
import net.spookly.hyprox.routing.DataPath;
import net.spookly.hyprox.routing.RoutingDecision;
import net.spookly.hyprox.routing.RoutingPlanner;
import net.spookly.hyprox.routing.RoutingRequest;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Objects;

/**
 * Handles the initial client handshake and issues referral redirects.
 */
public final class ProxyStreamHandler extends SimpleChannelInboundHandler<Packet> {
    private final RoutingPlanner routingPlanner;
    private final ProxySessionLimiter sessionLimiter;
    private boolean handled;
    private boolean sessionTracked;
    private String remoteAddress;

    public ProxyStreamHandler(HyproxConfig config, RoutingPlanner routingPlanner, ProxySessionLimiter sessionLimiter) {
        Objects.requireNonNull(config, "config");
        this.routingPlanner = Objects.requireNonNull(routingPlanner, "routingPlanner");
        this.sessionLimiter = Objects.requireNonNull(sessionLimiter, "sessionLimiter");
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
        clearHandshakeTimeout(ctx);
        if (remoteAddress != null && !sessionLimiter.tryAcquireHandshake(remoteAddress)) {
            sendDisconnect(ctx, "rate limited", DisconnectType.Disconnect);
            return;
        }
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
        if (cause instanceof ReadTimeoutException) {
            sendDisconnect(ctx, "handshake timeout", DisconnectType.Disconnect);
            return;
        }
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
        ctx.fireChannelInactive();
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

    private void ensureSessionContext(ChannelHandlerContext ctx) {
        Channel contextChannel = ctx.channel().parent() == null ? ctx.channel() : ctx.channel().parent();
        if (contextChannel.attr(ProxySessionContext.SESSION_CONTEXT).get() != null) {
            return;
        }
        X509Certificate certificate = resolveClientCertificate(contextChannel);
        ProxySessionContext context = ProxySessionContext.from(remoteAddress, certificate);
        contextChannel.attr(ProxySessionContext.SESSION_CONTEXT).set(context);
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
}
