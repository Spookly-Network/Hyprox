package net.spookly.hyprox.proxy;

import io.netty.channel.Channel;
import net.spookly.hyprox.routing.BackendTarget;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps the QUIC connection and stream to a backend server.
 */
public final class BackendConnection {
    private final BackendTarget backend;
    private final Channel datagramChannel;
    private final Channel quicChannel;
    private final Channel streamChannel;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public BackendConnection(BackendTarget backend,
                             Channel datagramChannel,
                             Channel quicChannel,
                             Channel streamChannel) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.datagramChannel = Objects.requireNonNull(datagramChannel, "datagramChannel");
        this.quicChannel = Objects.requireNonNull(quicChannel, "quicChannel");
        this.streamChannel = Objects.requireNonNull(streamChannel, "streamChannel");
        this.quicChannel.closeFuture().addListener(future -> datagramChannel.close());
    }

    public BackendTarget backend() {
        return backend;
    }

    public Channel streamChannel() {
        return streamChannel;
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (streamChannel.isActive()) {
            streamChannel.close();
        }
        if (quicChannel.isActive()) {
            quicChannel.close();
        }
        if (datagramChannel.isActive()) {
            datagramChannel.close();
        }
    }
}
