package net.spookly.hyprox.registry;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * JSON response envelope for registry APIs.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class RegistryResponse {
    public final boolean ok;
    public final String message;
    public final Object data;

    public static RegistryResponse ok(String message, Object data) {
        return new RegistryResponse(true, message, data);
    }

    public static RegistryResponse error(String message) {
        return new RegistryResponse(false, message, null);
    }
}
