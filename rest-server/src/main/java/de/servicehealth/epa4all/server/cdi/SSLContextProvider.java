package de.servicehealth.epa4all.server.cdi;

import de.health.service.cetp.config.KonnektorAuth;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Optional;

import static de.health.service.cetp.config.KonnektorAuth.CERTIFICATE;
import static de.servicehealth.utils.SSLUtils.KeyStoreType.PKCS12;
import static de.servicehealth.utils.SSLUtils.SslContextType.TLS;
import static javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm;

@ApplicationScoped
public class SSLContextProvider {

    private static final Logger log = LoggerFactory.getLogger(SSLContextProvider.class.getName());

    @Produces
    @Singleton
    @Named("gematikSSLContext")
    SSLContext gematikSslContext(X509TrustManager trustManager) throws GeneralSecurityException {
        SSLContext sslContext = SSLContext.getInstance(TLS.name());
        sslContext.init(null, new TrustManager[] { trustManager }, null);
        return sslContext;
    }

    @Produces
    @Singleton
    @Named("konnektorDefaultSSLContext")
    SSLContext konnektorDefaultSslContext(
        KonnektorDefaultConfig konnektorDefaultConfig,
        X509TrustManager trustManager,
        @Named("gematikSSLContext") SSLContext gematikSslContext
    ) {
        Optional<KonnektorAuth> auth = konnektorDefaultConfig.getAuth();
        Optional<String> certAuthStoreFile = konnektorDefaultConfig.getCertAuthStoreFile();
        Optional<String> certAuthStoreFilePassword = konnektorDefaultConfig.getCertAuthStoreFilePassword();
        if (auth.isPresent()
            && auth.get() == CERTIFICATE
            && certAuthStoreFile.isPresent()
            && certAuthStoreFilePassword.isPresent()
        ) {
            String password = certAuthStoreFilePassword.get();
            try (FileInputStream inputStream = new FileInputStream(certAuthStoreFile.get())) {
                SSLContext sslContext = SSLContext.getInstance(TLS.name());
                KeyStore keyStore = KeyStore.getInstance(PKCS12.name());
                keyStore.load(inputStream, password.toCharArray());
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, password.toCharArray());
                sslContext.init(keyManagerFactory.getKeyManagers(), new TrustManager[] { trustManager }, null);
                return sslContext;
            } catch (Exception e) {
                log.error("Error while creating KonnektorDefaultSSLContext", e);
                return gematikSslContext;
            }
        } else {
            return gematikSslContext;
        }
    }
}
