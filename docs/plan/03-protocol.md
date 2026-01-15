# Protocol inventory

Sources
- Packet definitions: `libs/HytaleServer/com/hypixel/hytale/protocol/packets`.
- Packet ids: `libs/HytaleServer/com/hypixel/hytale/protocol/PacketRegistry.java`.
- Server flow: `libs/HytaleServer/com/hypixel/hytale/server/core/io/handlers/*`.

Handshake and auth (observed flow)
1) Client -> server: `Connect` (id 0)
2) Authenticated path: `AuthGrant` (11) -> `AuthToken` (12) -> optional `ServerAuthToken` (13)
3) Dev/password path: `ConnectAccept` (14) -> `PasswordResponse` (15) -> `PasswordAccepted` (16) or `PasswordRejected` (17)
4) On success: transition to setup handler

Key packets and fields (proxy relevance)
- `Connect` (id 0): `protocolHash`, `clientType`, `language?`, `identityToken?`, `uuid`, `username`, `referralData?` (max 4096), `referralSource?`.
- `Disconnect` (id 1): `reason?`, `type` (`Disconnect` or `Crash`).
- `Status` (id 10): `name?`, `motd?`, `playerCount`, `maxPlayers`.
- `AuthGrant` (id 11): `authorizationGrant?`, `serverIdentityToken?`.
- `AuthToken` (id 12): `accessToken?`, `serverAuthorizationGrant?`.
- `ServerAuthToken` (id 13): `serverAccessToken?`, `passwordChallenge?`.
- `ConnectAccept` (id 14): `passwordChallenge?` (<= 64 bytes).
- `PasswordResponse` (id 15): `hash?` (<= 64 bytes).
- `PasswordAccepted` (id 16): empty.
- `PasswordRejected` (id 17): `newChallenge?`, `attemptsRemaining`.

Referral and redirect
- `ClientReferral` (id 18): `hostTo?` (HostAddress), `data?` (<= 4096 bytes).
- Referral connections surface on the next `Connect` via `referralData` and `referralSource`.
- `PlayerSetupConnectEvent.referToServer(...)` uses `ClientReferral` during setup to redirect.

Setup and world entry (observed in `SetupPacketHandler`)
- `WorldSettings` (id 20): world height + required assets (compressed).
- `WorldLoadProgress` (id 21), `WorldLoadFinished` (id 22).
- `RequestAssets` (id 23): client request for assets.
- `ViewRadius` (id 32), `PlayerOptions` (id 33).
- `ServerInfo` (id 223): server name, motd, max players.
- `SetClientId` (id 100): client id assignment.
- `JoinWorld` (id 104): clear world + fade + world uuid.

Seamless migration candidates
- `JoinWorld`, `SetClientId`, and setup packets above likely need replay/coordination when switching backends.
- During full proxy handoff, block or queue state-changing packets while Backend B is prepared.

Forwarding considerations
- Redirect/referral path can avoid full packet decode for gameplay traffic.
- Full proxy path must decode/encode at least the handshake/auth + setup packets listed above.
- Any token- or host-bearing fields (`identityToken`, `authorizationGrant`, `serverAuthorizationGrant`, referral host/data) must be treated as sensitive and only rewritten with a clear policy.
