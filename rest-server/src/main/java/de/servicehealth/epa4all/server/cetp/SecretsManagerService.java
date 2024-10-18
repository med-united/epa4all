package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.FallbackSecretsManager;
import de.servicehealth.config.KonnektorDefaultConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.utils.TransportUtils.getFakeTrustManagers;

@ApplicationScoped
public class SecretsManagerService implements FallbackSecretsManager {

    private static final Logger log = Logger.getLogger(SecretsManagerService.class.getName());

    private KeyManagerFactory keyManagerFactory;

    @Inject
    public SecretsManagerService(KonnektorDefaultConfig konnektorDefaultConfig) {
        initFromConfig(konnektorDefaultConfig);
    }

    @Override
    public KeyManagerFactory getKeyManagerFactory() {
        return keyManagerFactory;
    }

    public void initFromConfig(KonnektorDefaultConfig konnektorDefaultConfig) {
        String certAuthStoreFile = konnektorDefaultConfig.getCertAuthStoreFile();
        String certAuthStorePass = konnektorDefaultConfig.getCertAuthStoreFilePassword();

        try (FileInputStream certInputStream = new FileInputStream(certAuthStoreFile)) {
            createSSLContext(certAuthStorePass, certInputStream);
        } catch (Exception e) {
            log.log(Level.SEVERE, "There was a problem when creating the SSLContext:", e);
        }
    }

    public SSLContext createSSLContext(String certAuthStorePass, InputStream certInputStream) throws Exception {
        SSLContext sslContext = SSLContext.getInstance(SslContextType.TLS.getSslContextType());

        KeyStore ks = KeyStore.getInstance(KeyStoreType.PKCS12.getKeyStoreType());
        ks.load(certInputStream, certAuthStorePass.toCharArray());

        keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(ks, certAuthStorePass.toCharArray());

        sslContext.init(keyManagerFactory.getKeyManagers(), getFakeTrustManagers(), null);
        return sslContext;
    }

    @Getter
    public enum SslContextType {
        SSL("SSL"), TLS("TLS");

        private final String sslContextType;

        SslContextType(String sslContextType) {
            this.sslContextType = sslContextType;
        }

    }

    @Getter
    public enum KeyStoreType {
        JKS("jks"), PKCS12("pkcs12");

        private final String keyStoreType;

        KeyStoreType(String keyStoreType) {
            this.keyStoreType = keyStoreType;
        }
    }
}
