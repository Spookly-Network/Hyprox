package net.spookly.hyprox.proxy;

import java.util.Objects;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Coordinates auto-read with the writability of a paired channel.
 */
public final class BackpressureRelayHandler extends ChannelInboundHandlerAdapter {
    private final Channel inboundChannel;

    public BackpressureRelayHandler(Channel inboundChannel) {
        this.inboundChannel = Objects.requireNonNull(inboundChannel, "inboundChannel");
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (inboundChannel.isActive()) {
            inboundChannel.config().setAutoRead(ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (inboundChannel.isActive()) {
            inboundChannel.close();
        }
        super.channelInactive(ctx);
    }
}
