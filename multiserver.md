Hytale supports native mechanisms for routing players between servers. No reverse proxy like BungeeCord is required.

## Player Referral
Transfers a connected player to another server. The server sends a referral packet containing the target host, port, and an optional 4KB payload. The client opens a new connection to the target and presents the payload during handshake.

```PlayerRef.referToServer(@Nonnull final String host, final int port, @Nullable byte[] data)```
> ⚠️ Security Warning: The payload is transmitted through the client and can be tampered with. Sign payloads cryptographically (e.g., HMAC with a shared secret) so the receiving server can verify authenticity.

**Use cases**: Transferring players between game servers, passing session context, gating access behind matchmaking.

**Coming Soon**: Array of targets tried in sequence for fallback connections.

## Connection Redirect
During connection handshake, a server can reject the player and redirect them to a different server. The client automatically connects to the redirected address.

```PlayerSetupConnectEvent.referToServer(@Nonnull final String host, final int port, @Nullable byte[] data)```
**Use cases**: Load balancing, regional server routing, enforcing lobby-first connections.

## Disconnect Fallback
When a player is unexpectedly disconnected (server crash, network interruption), the client automatically reconnects to a pre-configured fallback server instead of returning to the main menu.

**Use cases**: Returning players to a lobby after game server crash, maintaining engagement during restarts.

**Coming Soon**: Fallback packet implementation expected within weeks after Early Access launch.

## Building a Proxy
Build custom proxy servers using Netty QUIC. Hytale uses QUIC exclusively for client-server communication.

Packet definitions and protocol structure are available in `HytaleServer.jar`:

```com.hypixel.hytale.protocol.packets```
Use these to decode, inspect, modify, or forward traffic between clients and backend servers.