package net.spookly.hyprox.proxy;

import com.hypixel.hytale.protocol.io.netty.ProtocolUtil;
import io.netty.channel.Channel;
import net.spookly.hyprox.routing.BackendReservation;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates lifecycle between a client stream and backend connection.
 */
public final class ProxyBridgeSession {
    private final Channel clientChannel;
    private final BackendConnection backendConnection;
    private final BackendReservation reservation;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ProxyBridgeSession(Channel clientChannel, BackendConnection backendConnection, BackendReservation reservation) {
        this.clientChannel = Objects.requireNonNull(clientChannel, "clientChannel");
        this.backendConnection = Objects.requireNonNull(backendConnection, "backendConnection");
        this.reservation = reservation;
    }

    public Channel clientChannel() {
        return clientChannel;
    }

    public BackendConnection backendConnection() {
        return backendConnection;
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ProtocolUtil.closeApplicationConnection(clientChannel);
        backendConnection.close();
        if (reservation != null) {
            reservation.release();
        }
    }
}
