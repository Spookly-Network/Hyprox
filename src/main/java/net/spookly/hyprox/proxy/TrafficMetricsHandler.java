package net.spookly.hyprox.proxy;

import java.util.Objects;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * Records byte counters for a data path stream.
 */
public final class TrafficMetricsHandler extends ChannelDuplexHandler {
    private final ProxyDataPathMetrics metrics;
    private final TrafficSide side;

    public TrafficMetricsHandler(ProxyDataPathMetrics metrics, TrafficSide side) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.side = Objects.requireNonNull(side, "side");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf buf) {
            recordInbound(buf.readableBytes());
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf buf) {
            recordOutbound(buf.readableBytes());
        }
        super.write(ctx, msg, promise);
    }

    private void recordInbound(int bytes) {
        if (side == TrafficSide.CLIENT_STREAM) {
            metrics.recordClientToBackendBytes(bytes);
        } else {
            metrics.recordBackendToClientBytes(bytes);
        }
    }

    private void recordOutbound(int bytes) {
        if (side == TrafficSide.CLIENT_STREAM) {
            metrics.recordBackendToClientBytes(bytes);
        } else {
            metrics.recordClientToBackendBytes(bytes);
        }
    }

    public enum TrafficSide {
        CLIENT_STREAM,
        BACKEND_STREAM
    }
}
