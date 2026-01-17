package net.spookly.hyprox.proxy;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;
import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.routing.BackendHealthProbe;
import net.spookly.hyprox.routing.BackendTarget;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * QUIC-based health probe that attempts a backend connection.
 */
public final class QuicBackendHealthProbe implements BackendHealthProbe {
    private final BackendConnector connector;
    private final boolean ownsConnector;

    /**
     * Create a probe backed by a dedicated QUIC connector.
     */
    public QuicBackendHealthProbe(HyproxConfig config) {
        this(new BackendConnector(config), true);
    }

    /**
     * Create a probe using a provided connector.
     */
    public QuicBackendHealthProbe(BackendConnector connector) {
        this(connector, false);
    }

    private QuicBackendHealthProbe(BackendConnector connector, boolean ownsConnector) {
        this.connector = Objects.requireNonNull(connector, "connector");
        this.ownsConnector = ownsConnector;
    }

    @Override
    public CompletableFuture<Boolean> probe(BackendTarget target, int timeoutMs) {
        Objects.requireNonNull(target, "target");
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Future<BackendConnection> connectFuture = connector.connect(target);
        final ScheduledFuture<?> timeoutFuture;
        if (timeoutMs > 0) {
            timeoutFuture = connector.workerGroup().next().schedule(() -> {
                if (result.complete(false)) {
                    connectFuture.cancel(false);
                }
            }, timeoutMs, TimeUnit.MILLISECONDS);
        } else {
            timeoutFuture = null;
        }
        connectFuture.addListener(future -> {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
            if (!future.isSuccess()) {
                result.complete(false);
                return;
            }
            BackendConnection connection = (BackendConnection) future.getNow();
            if (connection != null) {
                connection.close();
            }
            result.complete(true);
        });
        return result;
    }

    @Override
    public void close() {
        if (!ownsConnector) {
            return;
        }
        connector.workerGroup().shutdownGracefully();
    }
}
