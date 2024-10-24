package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.FallbackSecretsManager;
import de.servicehealth.config.KonnektorDefaultConfig;
import de.servicehealth.utils.SSLResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.utils.SSLUtils.initSSLContext;

@ApplicationScoped
public class SecretsManagerService implements FallbackSecretsManager {

    private static final Logger log = Logger.getLogger(SecretsManagerService.class.getName());

    private KeyManagerFactory keyManagerFactory;

    @Inject
    public SecretsManagerService(KonnektorDefaultConfig konnektorDefaultConfig) {
        initFromConfig(konnektorDefaultConfig);
    }

    public void initFromConfig(KonnektorDefaultConfig konnektorDefaultConfig) {
        String certAuthStorePass = konnektorDefaultConfig.getCertAuthStoreFilePassword();
        try (FileInputStream certInputStream = new FileInputStream(konnektorDefaultConfig.getCertAuthStoreFile())) {
            SSLResult sslResult = initSSLContext(certInputStream, certAuthStorePass);
            keyManagerFactory = sslResult.getKeyManagerFactory();
        } catch (Exception e) {
            log.log(Level.SEVERE, "There was a problem when creating the SSLContext:", e);
        }
    }

    @Override
    public KeyManagerFactory getKeyManagerFactory() {
        return keyManagerFactory;
    }
}