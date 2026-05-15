package de.servicehealth.epa4all.server.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
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
import java.util.Optional;

/**
 * Optional dedicated trust store containing the Konnektor's self-signed CA.
 * <p>
 * Some praxis Konnektoren are deployed with a self-signed (or private-CA-signed) TLS
 * certificate that is not anchored in the Gematik TSL. Validating those connections with
 * the global Gematik {@link X509TrustManager} would fail; disabling verification globally
 * would silently weaken every other TLS client. The fix is to narrow the trust set for
 * the Konnektor connection only — load the operator-supplied PKCS#12 with the Konnektor's
 * CA certificate(s) and produce a {@link X509TrustManager} dedicated to that store.
 * <p>
 * Configuration (all optional):
 * <ul>
 *   <li>{@code konnektor.self-signed.truststore.path} — filesystem path or classpath
 *       resource URI ({@code classpath:konnektor/self-signed-truststore.p12}). When the
 *       property is unset, the provider tries the default classpath resource
 *       {@code konnektor/self-signed-truststore.p12} and silently produces empty if absent.</li>
 *   <li>{@code konnektor.self-signed.truststore.password} — PKCS#12 integrity password.
 *       Required when the file is present.</li>
 * </ul>
 * <p>
 * If the property is explicitly set but the file or password is unusable, the provider
 * fails loudly: a misconfigured trust store must be fixed, not silently fall back to the
 * Gematik TSL (which would re-introduce the validation gap this feature exists to close).
 * <p>
 * Consumers ({@link de.servicehealth.epa4all.server.serviceport.ServicePortProvider})
 * inject this as {@code Optional<X509TrustManager>} and pass it to
 * {@code SSLUtils.createSSLContext(...)} in place of the global trust manager when present.
 */
@ApplicationScoped
public class KonnektorSelfSignedTrustManagerProvider {

    private static final Logger log = LoggerFactory.getLogger(KonnektorSelfSignedTrustManagerProvider.class);

    private static final String DEFAULT_CLASSPATH_RESOURCE = "konnektor/self-signed-truststore.p12";
    private static final String CLASSPATH_PREFIX = "classpath:";

    @Produces
    @Singleton
    @Named("konnektorSelfSignedTrustManager")
    Optional<X509TrustManager> trustManager(
        @ConfigProperty(name = "konnektor.self-signed.truststore.path") Optional<String> path,
        @ConfigProperty(name = "konnektor.self-signed.truststore.password") Optional<String> password
    ) {
        boolean explicit = path.isPresent();
        String effectivePath = path.orElse(CLASSPATH_PREFIX + DEFAULT_CLASSPATH_RESOURCE);

        try (InputStream in = open(effectivePath)) {
            if (in == null) {
                if (explicit) {
                    throw new IllegalStateException(
                        "konnektor.self-signed.truststore.path=" + effectivePath + " is not readable");
                }
                log.info("No konnektor self-signed truststore configured (default classpath resource {} absent); "
                    + "ServicePortProvider will use the Gematik TSL trust manager.", DEFAULT_CLASSPATH_RESOURCE);
                return Optional.empty();
            }
            if (password.isEmpty()) {
                throw new IllegalStateException(
                    "konnektor.self-signed.truststore.password is required when the truststore is present at "
                        + effectivePath);
            }
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, password.get().toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager x509) {
                    log.info("Loaded konnektor self-signed truststore from {} ({} accepted issuers)",
                        effectivePath, x509.getAcceptedIssuers().length);
                    return Optional.of(x509);
                }
            }
            throw new IllegalStateException("TrustManagerFactory produced no X509TrustManager from " + effectivePath);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to load konnektor self-signed truststore from " + effectivePath + ": " + e.getMessage(), e);
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
}
