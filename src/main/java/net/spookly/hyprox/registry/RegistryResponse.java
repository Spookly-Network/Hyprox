package net.spookly.hyprox.registry;

public final class RegistryResponse {
    public final boolean ok;
    public final String message;
    public final Object data;

    private RegistryResponse(boolean ok, String message, Object data) {
        this.ok = ok;
        this.message = message;
        this.data = data;
    }

    public static RegistryResponse ok(String message, Object data) {
        return new RegistryResponse(true, message, data);
    }

    public static RegistryResponse error(String message) {
        return new RegistryResponse(false, message, null);
    }
}
