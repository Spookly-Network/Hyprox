package net.spookly.hyprox.proxy;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.connection.ClientType;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.protocol.packets.connection.Disconnect;
import com.hypixel.hytale.protocol.packets.connection.DisconnectType;
import io.netty.channel.embedded.EmbeddedChannel;
import net.spookly.hyprox.routing.BackendSource;
import net.spookly.hyprox.routing.BackendTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullProxyForwardingIntegrationTest {
    @Test
    void forwardsPacketsInBothDirections() {
        EmbeddedChannel clientChannel = new EmbeddedChannel();
        EmbeddedChannel backendStream = new EmbeddedChannel();
        EmbeddedChannel backendQuic = new EmbeddedChannel();
        EmbeddedChannel backendDatagram = new EmbeddedChannel();
        BackendConnection connection = new BackendConnection(
                backendTarget("backend-1"),
                backendDatagram,
                backendQuic,
                backendStream
        );
        ProxyBridgeSession session = new ProxyBridgeSession(clientChannel, connection, null);
        ProxyDataPathMetrics metrics = new ProxyDataPathMetrics();

        clientChannel.pipeline().addLast(new PacketForwardingHandler(
                backendStream,
                session,
                metrics,
                PacketForwardingHandler.ForwardDirection.CLIENT_TO_BACKEND,
                null,
                false
        ));
        backendStream.pipeline().addLast(new PacketForwardingHandler(
                clientChannel,
                session,
                metrics,
                PacketForwardingHandler.ForwardDirection.BACKEND_TO_CLIENT,
                null,
                false
        ));

        Connect connect = connectPacket("client-1");
        clientChannel.writeInbound(connect);
        Packet forwardedToBackend = backendStream.readOutbound();
        assertSame(connect, forwardedToBackend);
        assertEquals(1, metrics.clientToBackendPackets());

        Connect response = connectPacket("client-1");
        backendStream.writeInbound(response);
        Packet forwardedToClient = clientChannel.readOutbound();
        assertSame(response, forwardedToClient);
        assertEquals(1, metrics.backendToClientPackets());

        closeChannels(clientChannel, backendStream, backendQuic, backendDatagram);
    }

    @Test
    void disconnectClosesBothSides() {
        EmbeddedChannel clientChannel = new EmbeddedChannel();
        EmbeddedChannel backendStream = new EmbeddedChannel();
        EmbeddedChannel backendQuic = new EmbeddedChannel();
        EmbeddedChannel backendDatagram = new EmbeddedChannel();
        BackendConnection connection = new BackendConnection(
                backendTarget("backend-2"),
                backendDatagram,
                backendQuic,
                backendStream
        );
        ProxyBridgeSession session = new ProxyBridgeSession(clientChannel, connection, null);
        ProxyDataPathMetrics metrics = new ProxyDataPathMetrics();

        backendStream.pipeline().addLast(new PacketForwardingHandler(
                clientChannel,
                session,
                metrics,
                PacketForwardingHandler.ForwardDirection.BACKEND_TO_CLIENT,
                null,
                false
        ));

        Disconnect disconnect = new Disconnect("bye", DisconnectType.Disconnect);
        backendStream.writeInbound(disconnect);
        Packet forwarded = clientChannel.readOutbound();
        assertSame(disconnect, forwarded);

        runPendingTasks(clientChannel, backendStream, backendQuic, backendDatagram);
        assertFalse(clientChannel.isOpen());
        assertFalse(backendStream.isOpen());
        assertFalse(backendQuic.isOpen());
        assertFalse(backendDatagram.isOpen());
    }

    private BackendTarget backendTarget(String id) {
        return new BackendTarget(
                id,
                "pool-1",
                "127.0.0.1",
                9000,
                1,
                100,
                List.of("full-proxy"),
                BackendSource.STATIC,
                false
        );
    }

    private Connect connectPacket(String name) {
        return new Connect(
                "hash",
                ClientType.Game,
                null,
                null,
                new UUID(0L, 1L),
                name,
                null,
                null
        );
    }

    private void closeChannels(EmbeddedChannel... channels) {
        for (EmbeddedChannel channel : channels) {
            channel.finishAndReleaseAll();
        }
    }

    private void runPendingTasks(EmbeddedChannel... channels) {
        for (EmbeddedChannel channel : channels) {
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();
        }
    }
}
