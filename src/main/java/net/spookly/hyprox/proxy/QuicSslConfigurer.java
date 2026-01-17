package net.spookly.hyprox.proxy;

import java.lang.reflect.Method;
import java.util.List;

import io.netty.handler.codec.quic.QuicSslContextBuilder;

/**
 * Applies optional TLS settings to QUIC SSL builders.
 */
final class QuicSslConfigurer {
    private QuicSslConfigurer() {
    }

    static void applyCipherSuites(QuicSslContextBuilder builder, List<String> cipherSuites) {
        if (cipherSuites == null || cipherSuites.isEmpty()) {
            return;
        }
        Method method = findCipherMethod(builder);
        if (method == null) {
            throw new IllegalStateException("proxy.quic.cipherSuites is not supported by this QUIC runtime");
        }
        try {
            method.invoke(builder, cipherSuites);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to apply proxy.quic.cipherSuites", e);
        }
    }

    private static Method findCipherMethod(QuicSslContextBuilder builder) {
        try {
            return builder.getClass().getMethod("ciphers", Iterable.class);
        } catch (NoSuchMethodException ignored) {
            try {
                return builder.getClass().getMethod("ciphers", List.class);
            } catch (NoSuchMethodException second) {
                return null;
            }
        }
    }
}
