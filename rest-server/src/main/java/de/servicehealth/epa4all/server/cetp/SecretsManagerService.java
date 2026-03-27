package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.ISecretsManager;
import de.health.service.cetp.config.KonnektorAuth;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.config.api.IUserConfigurations;
import de.servicehealth.utils.SSLContextBundle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.util.Optional;

import static de.health.service.cetp.config.KonnektorAuth.BASIC;
import static de.servicehealth.utils.SSLUtils.KeyStoreType.PKCS12;
import static de.servicehealth.utils.SSLUtils.createSSLContextBundle;
import static de.servicehealth.utils.SSLUtils.getClientCertificateBytes;

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
            String password = certAuthStoreFilePassword.get();
            try (FileInputStream inputStream = new FileInputStream(certAuthStoreFile.get())) {
                SSLContextBundle sslContextBundle = createSSLContextBundle(inputStream, password, PKCS12);
                keyManagerFactory = sslContextBundle.getKeyManagerFactory();
            } catch (Exception e) {
                log.error("There was a problem when creating the SSLContext", e);
            }
        }
    }

    @Override
    public KeyManagerFactory getKeyManagerFactory(KonnektorConfig config) {
        IUserConfigurations userConfigurations = config.getUserConfigurations();
        String certificate = userConfigurations.getClientCertificate();
        String password = userConfigurations.getClientCertificatePassword();
        if (KonnektorAuth.from(userConfigurations.getAuth()) == BASIC || certificate == null) {
            return keyManagerFactory;
        } else {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(getClientCertificateBytes(certificate))) {
                SSLContextBundle sslContextBundle = createSSLContextBundle(inputStream, password, PKCS12);
                return sslContextBundle.getKeyManagerFactory();
            } catch (Exception e) {
                log.error("Could not create keyManagerFactory", e);
            }
        }
        return null;
    }
}