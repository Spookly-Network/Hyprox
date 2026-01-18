package net.spookly.hyprox.proxy;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import net.spookly.hyprox.config.HyproxConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BackendConnectorTest {
    @Test
    void buildsSslContextWithCertAndKey() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);
        try {
            HyproxConfig config = new HyproxConfig();
            config.proxy = new HyproxConfig.ProxyConfig();
            config.proxy.quic = new HyproxConfig.QuicConfig();
            config.proxy.quic.alpn = List.of("hytale/1");
            config.proxy.quic.cert = certificate.certificate().getAbsolutePath();
            config.proxy.quic.key = certificate.privateKey().getAbsolutePath();

            assertDoesNotThrow(() -> new BackendConnector(config, workerGroup));
        } finally {
            workerGroup.shutdownGracefully().syncUninterruptibly();
            cleanupCertificate(certificate);
        }
    }

    private void cleanupCertificate(SelfSignedCertificate certificate) throws IOException {
        if (certificate == null) {
            return;
        }
        Files.deleteIfExists(certificate.certificate().toPath());
        Files.deleteIfExists(certificate.privateKey().toPath());
    }
}
