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
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Optional;

import static de.health.service.cetp.config.KonnektorAuth.BASIC;
import static de.servicehealth.utils.SSLUtils.KeyStoreType.PKCS12;
import static de.servicehealth.utils.SSLUtils.createSSLContextBundle;
import static de.servicehealth.utils.SSLUtils.getClientCertificateBytes;

@ApplicationScoped
public class SecretsManagerService implements ISecretsManager {

    private static final Logger log = LoggerFactory.getLogger(SecretsManagerService.class.getName());

    private final X509TrustManager trustManager;
    private KeyManagerFactory keyManagerFactory;

    @Inject
    public SecretsManagerService(
        KonnektorDefaultConfig konnektorDefaultConfig,
        X509TrustManager trustManager
    ) {
        this.trustManager = trustManager;
        initFromConfig(konnektorDefaultConfig);
    }

    private void initFromConfig(KonnektorDefaultConfig konnektorDefaultConfig) {
        Optional<String> certAuthStoreFile = konnektorDefaultConfig.getCertAuthStoreFile();
        Optional<String> certAuthStoreFilePassword = konnektorDefaultConfig.getCertAuthStoreFilePassword();
        if (certAuthStoreFile.isPresent() && certAuthStoreFilePassword.isPresent()) {
            String password = certAuthStoreFilePassword.get();
            try (FileInputStream inputStream = new FileInputStream(certAuthStoreFile.get())) {
                SSLContextBundle sslContextBundle = createSSLContextBundle(inputStream, password, trustManager, PKCS12);
                keyManagerFactory = sslContextBundle.getKeyManagerFactory();
            } catch (Exception e) {
                log.error("There was a problem when creating the SSLContext", e);
            }
        }
    }

    @Override
    public KeyManagerFactory getKeyManagerFactory(KonnektorConfig config) {
        // The Konnektor's CETP TLS client offers RSA-only cipher suites, while the
        // client-system cert used for SOAP mTLS is ECC. Allow a dedicated (RSA)
        // keystore for the CETP server: path via system property, password via env
        // so it never shows up in argv/ps.
        //
        // Multi-Konnektor: each secunet Konnektor PINS the client-system cert its
        // provider registered, so one global keystore can only ever serve pharmacies
        // whose Konnektors trust the same cert. A per-Konnektor cetp-server.p12 in
        // the konnektoren/<port>/ dir takes precedence; password from
        // CETP_SERVER_STORE_PASSWORD_<port>, falling back to the shared env var.
        File perKonnektorStore = new File(config.getFolder(), "cetp-server.p12");
        if (perKonnektorStore.isFile()) {
            String perKonnektorPass = System.getenv().getOrDefault(
                "CETP_SERVER_STORE_PASSWORD_" + config.getCetpPort(),
                System.getenv().getOrDefault("CETP_SERVER_STORE_PASSWORD", "")
            );
            try (FileInputStream inputStream = new FileInputStream(perKonnektorStore)) {
                return createSSLContextBundle(inputStream, perKonnektorPass, trustManager, PKCS12).getKeyManagerFactory();
            } catch (Exception e) {
                log.error("Could not create keyManagerFactory from " + perKonnektorStore.getAbsolutePath(), e);
            }
        }
        String cetpStore = System.getProperty("cetp.server.cert.auth.store.file");
        if (cetpStore != null) {
            String cetpPass = System.getenv().getOrDefault("CETP_SERVER_STORE_PASSWORD", "");
            try (FileInputStream inputStream = new FileInputStream(cetpStore)) {
                return createSSLContextBundle(inputStream, cetpPass, trustManager, PKCS12).getKeyManagerFactory();
            } catch (Exception e) {
                log.error("Could not create keyManagerFactory from cetp.server.cert.auth.store.file", e);
            }
        }
        IUserConfigurations userConfigurations = config.getUserConfigurations();
        String certificate = userConfigurations.getClientCertificate();
        String password = userConfigurations.getClientCertificatePassword();
        if (KonnektorAuth.from(userConfigurations.getAuth()) == BASIC || certificate == null) {
            return keyManagerFactory;
        } else {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(getClientCertificateBytes(certificate))) {
                SSLContextBundle sslContextBundle = createSSLContextBundle(inputStream, password, trustManager, PKCS12);
                return sslContextBundle.getKeyManagerFactory();
            } catch (Exception e) {
                log.error("Could not create keyManagerFactory", e);
            }
        }
        return null;
    }
}