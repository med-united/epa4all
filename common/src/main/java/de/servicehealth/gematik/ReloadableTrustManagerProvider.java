package de.servicehealth.gematik;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Optional;

import static de.servicehealth.setup.SystemPropertyService.getQuarkusProfile;
import static de.servicehealth.setup.SystemPropertyService.isPuRuRefProfile;

@ApplicationScoped
public class ReloadableTrustManagerProvider {

    private static final Logger log = LoggerFactory.getLogger(ReloadableTrustManagerProvider.class);

    private static final Duration MIN_RELOAD_INTERVAL = Duration.ofSeconds(60);

    private static final String DEFAULT_NON_TSL_TRUSTSTORE_RESOURCE = "test-truststore/test-truststore.p12";
    private static final String DEFAULT_NON_TSL_TRUSTSTORE_PASSWORD = "password";
    private static final String CLASSPATH_PREFIX = "classpath:";

    @Inject
    @ConfigProperty(name = "ere.per.konnektor.config.folder")
    String konnektorConfigFolder;

    @Inject
    @ConfigProperty(name = "non-tsl.truststore.path")
    Optional<String> nonTslTrustStorePath;

    @Inject
    @ConfigProperty(name = "non-tsl.truststore.password")
    Optional<String> nonTslTrustStorePassword;

    private volatile X509TrustManager trustManager;

    @Produces
    @Singleton
    public synchronized X509TrustManager trustManager() throws Exception {
        if (trustManager == null) {
            if (isPuRuRefProfile()) {
                GematikEnvironment env = GematikEnvironment.fromQuarkusProfile(getQuarkusProfile());
                Path cacheFile = Path.of(konnektorConfigFolder, "gematik-tsl-cache." + env.name() + ".p12");
                log.info("Initializing Gematik trust store for environment {} (cache={})", env, cacheFile);
                CachingGematikLoader loader = new CachingGematikLoader(env, cacheFile);
                trustManager = new ReloadableX509TrustManager(loader, MIN_RELOAD_INTERVAL);
            } else {
                trustManager = loadNonTslTrustManager();
            }
        }
        return trustManager;
    }

    /**
     * Outside PU/RU/REF (i.e. dev / wiremock / mTLS / *-test profiles) the service does not
     * talk to TI, so the Gematik TSL trust set is irrelevant. Previously these profiles were
     * served a no-op "trust everything" {@link X509TrustManager}, which is a latent
     * trust-all path in the production binary regardless of guards. Replaced with a real
     * trust manager backed by a small PKCS#12 containing only the CAs those profiles
     * actually need (wiremock self-signed CA, etc.). Fail-loud if missing — operators must
     * either bundle the truststore on the classpath ({@code common-test} provides
     * {@value #DEFAULT_NON_TSL_TRUSTSTORE_RESOURCE} for tests) or point
     * {@code non-tsl.truststore.path} at one. The trust-all branch is gone.
     */
    private X509TrustManager loadNonTslTrustManager() throws Exception {
        boolean explicit = nonTslTrustStorePath.isPresent();
        String location = nonTslTrustStorePath.orElse(CLASSPATH_PREFIX + DEFAULT_NON_TSL_TRUSTSTORE_RESOURCE);
        String password = nonTslTrustStorePassword.orElse(DEFAULT_NON_TSL_TRUSTSTORE_PASSWORD);

        try (InputStream in = open(location)) {
            if (in == null) {
                throw new IllegalStateException(
                    "Non-TSL trust store not found at " + location + " (profile=" + getQuarkusProfile()
                        + ", explicit=" + explicit + "). For tests, depend on `common-test` which bundles "
                        + DEFAULT_NON_TSL_TRUSTSTORE_RESOURCE + "; for non-test runs in non-PU/RU/REF profiles, "
                        + "set `non-tsl.truststore.path` (filesystem or classpath:...) and `non-tsl.truststore.password`. "
                        + "The previous trust-all default has been removed.");
            }
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, password.toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager x509) {
                    log.info("Loaded non-TSL trust store from {} ({} accepted issuers, profile={})",
                        location, x509.getAcceptedIssuers().length, getQuarkusProfile());
                    return x509;
                }
            }
            throw new IllegalStateException(
                "TrustManagerFactory produced no X509TrustManager from " + location);
        }
    }

    private InputStream open(String location) throws Exception {
        if (location.startsWith(CLASSPATH_PREFIX)) {
            String resource = location.substring(CLASSPATH_PREFIX.length());
            return Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        }
        Path p = Path.of(location);
        return Files.isReadable(p) ? new FileInputStream(p.toFile()) : null;
    }

    void disposeTrustManager(@Disposes @Singleton X509TrustManager trustManager) {
        if (trustManager instanceof ReloadableX509TrustManager tm) {
            tm.close();
        }
    }

    @PreDestroy
    void shutdown() {
        if (trustManager instanceof ReloadableX509TrustManager tm) {
            tm.close();
        }
    }
}
