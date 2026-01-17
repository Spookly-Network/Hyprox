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
import net.spookly.hyprox.auth.ReferralService;
import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.routing.BackendTarget;
import net.spookly.hyprox.routing.BackendReservation;
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
    private final ReferralService referralService;
    private boolean handled;
    private boolean sessionTracked;
    private String remoteAddress;
    private BackendReservation backendReservation;

    public ProxyStreamHandler(HyproxConfig config,
                              RoutingPlanner routingPlanner,
                              ProxySessionLimiter sessionLimiter,
                              ReferralService referralService) {
        Objects.requireNonNull(config, "config");
        this.routingPlanner = Objects.requireNonNull(routingPlanner, "routingPlanner");
        this.sessionLimiter = Objects.requireNonNull(sessionLimiter, "sessionLimiter");
        this.referralService = Objects.requireNonNull(referralService, "referralService");
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
            releaseReservation();
            sendDisconnect(ctx, "full proxy path not yet enabled", DisconnectType.Disconnect);
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
