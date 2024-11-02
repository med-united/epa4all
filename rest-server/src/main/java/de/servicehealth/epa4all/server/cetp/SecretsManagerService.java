package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.ISecretsManager;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.config.api.IUserConfigurations;
import de.servicehealth.utils.SSLResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.net.ssl.KeyManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.utils.SSLUtils.getClientCertificateBytes;
import static de.servicehealth.utils.SSLUtils.initSSLContext;

@ApplicationScoped
public class SecretsManagerService implements ISecretsManager {

    private static final Logger log = Logger.getLogger(SecretsManagerService.class.getName());

    private KeyManagerFactory keyManagerFactory;

    @Inject
    public SecretsManagerService(KonnektorDefaultConfig konnektorDefaultConfig) {
        initFromConfig(konnektorDefaultConfig);
    }

    private void initFromConfig(KonnektorDefaultConfig konnektorDefaultConfig) {
        String certAuthStorePass = konnektorDefaultConfig.getCertAuthStoreFilePassword();
        try (FileInputStream certInputStream = new FileInputStream(konnektorDefaultConfig.getCertAuthStoreFile())) {
            SSLResult sslResult = initSSLContext(certInputStream, certAuthStorePass);
            keyManagerFactory = sslResult.getKeyManagerFactory();
        } catch (Exception e) {
            log.log(Level.SEVERE, "There was a problem when creating the SSLContext:", e);
        }
    }

    @Override
    public KeyManagerFactory getKeyManagerFactory(KonnektorConfig config) {
        IUserConfigurations userConfigurations = config.getUserConfigurations();
        String clientCertificate = userConfigurations.getClientCertificate();
        if (clientCertificate == null) {
            return keyManagerFactory;
        } else {
            byte[] clientCertificateBytes = getClientCertificateBytes(clientCertificate);
            try (ByteArrayInputStream certInputStream = new ByteArrayInputStream(clientCertificateBytes)) {
                SSLResult sslResult = initSSLContext(certInputStream, userConfigurations.getClientCertificatePassword());
                return sslResult.getKeyManagerFactory();
            } catch (Exception e) {
                log.log(Level.SEVERE, "Could not create keyManagerFactory", e);
            }
        }
        return null;
    }
}