package de.servicehealth.epa4all.server.cdi;

import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import de.servicehealth.api.epa4all.EpaConfig;
import de.servicehealth.gematik.A24624VerificationException;
import de.servicehealth.gematik.A24624Verifier;
import de.servicehealth.gematik.VauAutCertSupplier;
import de.servicehealth.gematik.VauAutCertificateWithChain;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static de.servicehealth.setup.SystemPropertyService.isPuProfile;
import static de.servicehealth.setup.SystemPropertyService.isPuRuRefProfile;

/**
 * {@link VauAutCertSupplier} that fetches the VAU server's AUT certificate on-demand from
 * the VAU CertData endpoint defined in gemSpec_Krypt A_24957 / gemSpec_ePA_FdV §6.1.3:
 * <pre>{@code
 * GET https://<backend>/CertData.<hex(certHash)>-<cdv>
 * }</pre>
 * (note the {@code .} between {@code CertData} and the hash, and {@code -} between the hash
 * and the {@code cdv} — see med-united/lib-vau commit f335017 for the reference impl).
 * <p>
 * Response body is CBOR-encoded {@link VauAutCertificateWithChain} carrying the AUT cert
 * plus its issuing CA and cross-cert chain. We use {@code cert} as the AUT cert; chain
 * material is currently unused but logged for traceability.
 * <p>
 * Cached by {@code (backend, certHash, cdv)} for the cert's lifetime; rotation surfaces as
 * a new {@code certHash}. TLS layer validates against the Gematik TSL via
 * {@code gematikSslContext}; the returned cert's SHA-256 is rechecked against {@code certHash}
 * as defence in depth.
 * <p>
 * No-op in non-PU/RU/REF profiles ({@link A24624Verifier} is also off there).
 * <p>
 * <b>EPA-529:</b> a backend whose {@code gematik.vau.skip-aut-cert-checks[i]=true} returns
 * {@code null} (no fetch) in EVERY profile, including pu. Under pu this is a deliberate,
 * dangerous bypass — the VAU server is left unauthenticated and advisory GHSA-vvh7-x6c7-46gh
 * is re-opened for that backend. See the WARN logs in {@code get()} / {@code loadSkipMap()}.
 */
@ApplicationScoped
public class CertDataVauAutCertSupplier implements VauAutCertSupplier {

    private static final Logger log = LoggerFactory.getLogger(CertDataVauAutCertSupplier.class);

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final CBORMapper CBOR_MAPPER = new CBORMapper();

    private final SSLContext gematikSslContext;
    private final EpaConfig epaConfig;
    private final Optional<List<Boolean>> skipPerBackendConfig;
    private final boolean enabled;
    private final boolean puProfile;
    private final ConcurrentMap<String, X509Certificate> cache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> skipByBackend = new HashMap<>();
    private HttpClient httpClient;

    @Inject
    public CertDataVauAutCertSupplier(
        @Named("gematikSSLContext") SSLContext gematikSslContext,
        EpaConfig epaConfig,
        @ConfigProperty(name = "gematik.vau.skip-aut-cert-checks") Optional<List<Boolean>> skipPerBackendConfig
    ) {
        this.gematikSslContext = gematikSslContext;
        this.epaConfig = epaConfig;
        this.skipPerBackendConfig = skipPerBackendConfig;
        this.enabled = isPuRuRefProfile();
        this.puProfile = isPuProfile();
    }

    @PostConstruct
    void init() {
        if (!enabled) {
            log.info("CertDataVauAutCertSupplier: profile is not PU/RU/REF, disabled (A_24624 verifier is also disabled here)");
            return;
        }
        httpClient = HttpClient.newBuilder()
            .sslContext(gematikSslContext)
            .connectTimeout(HTTP_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        loadSkipMap();
        log.info("CertDataVauAutCertSupplier: ready (skip-aut-cert-checks={})", skipByBackend);
    }

    private void loadSkipMap() {
        if (skipPerBackendConfig.isEmpty() || skipPerBackendConfig.get().isEmpty()) {
            return;
        }
        List<Boolean> flags = skipPerBackendConfig.get();
        if (flags.size() != epaConfig.getEpaBackends().size()) {
            throw new IllegalStateException(
                "epa.backend[*] (" + epaConfig.getEpaBackends().size() + " entries) and "
                    + "gematik.vau.skip-aut-cert-checks[*] (" + flags.size() + " entries) must have the "
                    + "same number of entries; entry i is the skip flag for the backend at index i.");
        }
        Iterator<String> backendIter = epaConfig.getEpaBackends().iterator();
        Iterator<Boolean> flagIter = flags.iterator();
        while (backendIter.hasNext() && flagIter.hasNext()) {
            skipByBackend.put(backendIter.next(), flagIter.next());
        }
        if (puProfile && skipByBackend.containsValue(Boolean.TRUE)) {
            log.warn("DANGER (EPA-529): gematik.vau.skip-aut-cert-checks has true entries AND the "
                + "profile is PU (production). As of EPA-529 these ARE HONORED under PU: the A_24624-01 "
                + "cert-dependent checks are bypassed and the VAU server is NOT authenticated for the "
                + "affected backend(s). This re-opens advisory GHSA-vvh7-x6c7-46gh (server-auth bypass) "
                + "for those backends. Skip flags: {}", skipByBackend);
        }
    }

    @Override
    public X509Certificate get(String backend, byte[] certHash, int cdv) {
        if (!enabled) {
            return null;
        }
        if (Boolean.TRUE.equals(skipByBackend.get(backend))) {
            if (puProfile) {
                log.warn("DANGER (EPA-529): skipping AUT cert fetch for backend {} under the PU "
                    + "(production) profile (gematik.vau.skip-aut-cert-checks=true). The VAU server is "
                    + "NOT cryptographically authenticated for this backend — a network MITM can hijack "
                    + "the session (see advisory GHSA-vvh7-x6c7-46gh). Only OCSP/exp checks run.", backend);
            } else {
                log.warn("Skipping AUT cert fetch for backend {} (gematik.vau.skip-aut-cert-checks=true). "
                    + "Cert-dependent A_24624 checks will be bypassed (ru/ref test environment).", backend);
            }
            return null;
        }
        if (backend == null || certHash == null || certHash.length == 0) {
            throw new A24624VerificationException(
                "CertData fetch requires non-null backend and non-empty certHash; got backend=" + backend
                    + " certHashLen=" + (certHash == null ? 0 : certHash.length));
        }
        String cacheKey = backend + ":" + HexFormat.of().formatHex(certHash) + ":" + cdv;
        return cache.computeIfAbsent(cacheKey, k -> fetch(backend, certHash, cdv));
    }

    private X509Certificate fetch(String backend, byte[] certHash, int cdv) {
        String url = "https://" + backend + "/CertData." + HexFormat.of().formatHex(certHash) + "-" + cdv;
        log.info("Fetching VAU AUT cert: {}", url);
        try {
            HttpResponse<byte[]> resp = httpClient.send(
                HttpRequest.newBuilder(URI.create(url))
                    .header("x-useragent", epaConfig.getEpaUserAgent())
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray()
            );
            if (resp.statusCode() / 100 != 2) {
                throw new A24624VerificationException(
                    "CertData fetch failed: HTTP " + resp.statusCode() + " from " + url);
            }
            VauAutCertificateWithChain bundle = CBOR_MAPPER.readValue(resp.body(), VauAutCertificateWithChain.class);
            if (bundle.getCert() == null || bundle.getCert().length == 0) {
                throw new A24624VerificationException(
                    "CertData returned empty 'cert' field from " + url);
            }
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(bundle.getCert()));
            byte[] actual = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            if (!Arrays.equals(actual, certHash)) {
                throw new A24624VerificationException(
                    "CertData returned cert with wrong hash from " + url
                        + " (expected=" + HexFormat.of().formatHex(certHash)
                        + ", actual=" + HexFormat.of().formatHex(actual) + ")");
            }
            int chainLen = bundle.getRca_chain() == null ? 0 : bundle.getRca_chain().length;
            log.info("Fetched VAU AUT cert from {} (subject={}, serial={}, ca={}, rca_chain={} entries)",
                url, cert.getSubjectX500Principal(), cert.getSerialNumber(),
                bundle.getCa() != null ? "present" : "absent", chainLen);
            return cert;
        } catch (A24624VerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new A24624VerificationException("CertData fetch failed for " + url + ": " + e.getMessage(), e);
        }
    }
}
