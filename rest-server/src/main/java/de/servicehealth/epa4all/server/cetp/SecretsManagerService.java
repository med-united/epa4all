package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.ISecretsManager;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.config.api.IUserConfigurations;
import de.servicehealth.utils.SSLResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.util.Optional;

import static de.servicehealth.utils.SSLUtils.getClientCertificateBytes;
import static de.servicehealth.utils.SSLUtils.initSSLContext;

@ApplicationScoped
public class SecretsManagerService implements ISecretsManager {

    private static final Logger log = LoggerFactory.getLogger(SecretsManagerService.class.getName());

    private KeyManagerFactory keyManagerFactory;

    @Inject
    public SecretsManagerService(KonnektorDefaultConfig konnektorDefaultConfig) {
        initFromConfig(konnektorDefaultConfig);
    }

    private void initFromConfig(KonnektorDefaultConfig konnektorDefaultConfig) {
        Optional<String> certAuthStoreFile = konnektorDefaultConfig.getCertAuthStoreFile();
        Optional<String> certAuthStoreFilePassword = konnektorDefaultConfig.getCertAuthStoreFilePassword();
        if (certAuthStoreFile.isPresent() && certAuthStoreFilePassword.isPresent()) {
            String certAuthStorePass = certAuthStoreFilePassword.get();
            try (FileInputStream certInputStream = new FileInputStream(certAuthStoreFile.get())) {
                SSLResult sslResult = initSSLContext(certInputStream, certAuthStorePass);
                keyManagerFactory = sslResult.getKeyManagerFactory();
            } catch (Exception e) {
                log.error("There was a problem when creating the SSLContext", e);
            }
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
                log.error("Could not create keyManagerFactory", e);
            }
        }
        return null;
    }
}