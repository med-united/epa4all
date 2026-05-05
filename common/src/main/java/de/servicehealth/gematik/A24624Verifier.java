package de.servicehealth.gematik;

import de.gematik.vau.lib.data.SignedPublicVauKeys;
import de.gematik.vau.lib.data.VauPublicKeys;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers;
import org.bouncycastle.asn1.isismtt.x509.AdmissionSyntax;
import org.bouncycastle.asn1.isismtt.x509.Admissions;
import org.bouncycastle.asn1.isismtt.x509.ProfessionInfo;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.servicehealth.setup.SystemPropertyService.isPuRuRefProfile;

/**
 * Implements the A_24624-01 verification specified by gemSpec_Krypt for the
 * server-side {@code SignedPublicVauKeys} extracted from VAU handshake message M2.
 * <p>
 * The six checks are:
 * <ol>
 *   <li>Server AUT certificate chains to a TI-PKI trust anchor (via the Gematik TSL trust manager).</li>
 *   <li>OCSP response is present, signature-valid, and {@code producedAt} is within 24 hours of now.</li>
 *   <li>Server AUT certificate is within its own {@code notBefore}/{@code notAfter} window.</li>
 *   <li>Server AUT certificate carries the role OID {@code 1.2.276.0.76.4.209} (oid_epa_vau).</li>
 *   <li>{@code signature-ES256} verifies under the certificate's public key over {@code signed_pub_keys}.</li>
 *   <li>{@code exp} field of the parsed {@link VauPublicKeys} is strictly greater than the current time.</li>
 * </ol>
 * <p>
 * In non-PU/RU/REF profiles this verifier is a no-op (mirrors {@link ReloadableTrustManagerProvider}'s
 * fake-trust-manager pattern), so wiremock-driven tests don't fail on canned handshake bytes.
 */
@SuppressWarnings("CdiInjectionPointsInspection")
@ApplicationScoped
public class A24624Verifier {

    private static final Logger log = LoggerFactory.getLogger(A24624Verifier.class);

    /** Default OID for the {@code oid_epa_vau} role (ePA VAU server).
     *  Override via config key {@code gematik.vau.role-oid} if Gematik's spec/registry
     *  publishes a different value (some references use {@code 1.2.276.0.76.4.261}).
     */
    public static final String DEFAULT_OID_EPA_VAU = "1.2.276.0.76.4.209";

    private static final Duration MAX_OCSP_AGE = Duration.ofHours(24);
    private static final String OCSP_SIGNING_EKU = KeyPurposeId.id_kp_OCSPSigning.getId();

    private final X509TrustManager trustManager;
    private final String oidEpaVau;
    private boolean enabled;

    @Inject
    public A24624Verifier(
        X509TrustManager trustManager,
        @ConfigProperty(name = "gematik.vau.role-oid", defaultValue = DEFAULT_OID_EPA_VAU) String oidEpaVau
    ) {
        this.trustManager = trustManager;
        this.oidEpaVau = oidEpaVau;
    }

    @PostConstruct
    void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        enabled = isPuRuRefProfile();
        log.info("A_24624-01 verification enabled={} role-oid={}", enabled, oidEpaVau);
    }

    /**
     * Runs the A_24624-01 checks. Throws {@link A24624VerificationException} on the
     * first failure. In non-PU/RU/REF profiles this is a no-op.
     * <p>
     * Cert-independent OCSP / exp checks always run when the verifier is enabled. The
     * cert-dependent checks (cert hash binding, OCSP signature/responder authorization,
     * OCSP certID match, chain validation, temporal validity, role OID, ES256 signature)
     * run only when {@code autCert} is non-null. The supplier ({@code VauAutCertSupplier})
     * is the policy gate — it returns null for backends explicitly opted out via
     * {@code gematik.vau.skip-aut-cert-checks[i]=true}, and only ever throws on real fetch
     * failures. So a null {@code autCert} here means "intentional skip", logged as WARN.
     */
    public void verify(SignedPublicVauKeys signedKeys, X509Certificate autCert) {
        if (!enabled) {
            return;
        }
        if (signedKeys == null) {
            throw new A24624VerificationException("SignedPublicVauKeys is null");
        }

        // Always-on cert-independent checks.
        BasicOCSPResp basicOcsp = parseOcsp(signedKeys.getOcspResponse());
        checkOcspFreshness(basicOcsp);
        verifyOcspCertStatusGood(basicOcsp);
        verifyExpInFuture(signedKeys);

        if (autCert == null) {
            log.warn("A_24624-01 cert-dependent checks SKIPPED — supplier returned no autCert "
                + "(gematik.vau.skip-aut-cert-checks for this backend).");
            return;
        }

        verifyCertHashBinding(autCert, signedKeys.getCertHash());
        verifyOcspSignature(basicOcsp, autCert);
        verifyOcspCertIdMatchesAutCert(basicOcsp, autCert);
        verifyChainToTrustAnchor(autCert);
        verifyTemporalValidity(autCert);
        verifyRoleOid(autCert);
        verifyEs256Signature(autCert, signedKeys);
    }

    /**
     * Confirms the externally-supplied AUT cert is the one the M2 payload was signed against
     * by comparing {@code SHA-256(autCert.getEncoded())} to {@code signedKeys.certHash}. This is
     * the only binding between the externally-sourced cert and the in-band signed payload.
     */
    private void verifyCertHashBinding(X509Certificate autCert, byte[] expectedSha256) {
        if (expectedSha256 == null || expectedSha256.length != 32) {
            throw new A24624VerificationException("certHash missing or wrong length in SignedPublicVauKeys");
        }
        try {
            byte[] actual = MessageDigest.getInstance("SHA-256").digest(autCert.getEncoded());
            if (!Arrays.equals(actual, expectedSha256)) {
                throw new A24624VerificationException(
                    "Supplied autCert hash does not match signedKeys.certHash"
                        + " (expected=" + java.util.HexFormat.of().formatHex(expectedSha256)
                        + ", actual=" + java.util.HexFormat.of().formatHex(actual) + ")");
            }
        } catch (A24624VerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new A24624VerificationException("Failed to compute autCert hash for binding check", e);
        }
    }

    /**
     * RFC 6960 / TUC_PKI_006: each {@code SingleResponse.certID} must identify the certificate
     * being checked, expressed as {@code (issuerNameHash, issuerKeyHash, serialNumber)} under
     * SHA-1. We rebuild the expected {@link CertificateID} from the AUT cert plus its issuer
     * (looked up in the OCSP-embedded certs by subject DN) and compare it against every
     * SingleResp in the body.
     * <p>
     * The upstream {@code certHash} check binds the OCSP-embedded AUT cert to the signed
     * payload; this step closes the remaining loophole where a hostile responder could embed
     * a GOOD SingleResponse for a different serial.
     */
    private void verifyOcspCertIdMatchesAutCert(BasicOCSPResp basicOcsp, X509Certificate autCert) {
        X509CertificateHolder issuerHolder = findIssuerHolder(basicOcsp, autCert);
        CertificateID expected;
        try {
            DigestCalculator dc = new JcaDigestCalculatorProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build()
                .get(CertificateID.HASH_SHA1);
            expected = new CertificateID(dc, issuerHolder, autCert.getSerialNumber());
        } catch (Exception e) {
            throw new A24624VerificationException("Failed to build expected OCSP CertificateID", e);
        }
        for (SingleResp sr : basicOcsp.getResponses()) {
            if (!expected.equals(sr.getCertID())) {
                throw new A24624VerificationException(
                    "OCSP SingleResponse CertID does not match AUT cert (serial=" + autCert.getSerialNumber() + ")");
            }
        }
    }

    private X509CertificateHolder findIssuerHolder(BasicOCSPResp basicOcsp, X509Certificate autCert) {
        // (1) The issuer is sometimes embedded in the OCSP response alongside the responder cert.
        try {
            JcaX509CertificateConverter conv = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME);
            for (X509CertificateHolder holder : basicOcsp.getCerts()) {
                X509Certificate candidate = conv.getCertificate(holder);
                if (candidate.getSubjectX500Principal().equals(autCert.getIssuerX500Principal())) {
                    return holder;
                }
            }
        } catch (CertificateException e) {
            throw new A24624VerificationException("Failed to scan OCSP-embedded certs for AUT cert issuer", e);
        }
        // (2) Otherwise the issuer should be a trust anchor in our TSL truststore.
        for (X509Certificate ca : trustManager.getAcceptedIssuers()) {
            if (ca.getSubjectX500Principal().equals(autCert.getIssuerX500Principal())) {
                try {
                    return new X509CertificateHolder(ca.getEncoded());
                } catch (Exception e) {
                    throw new A24624VerificationException(
                        "Failed to wrap TSL trust anchor as X509CertificateHolder for "
                            + autCert.getIssuerX500Principal(), e);
                }
            }
        }
        throw new A24624VerificationException(
            "AUT cert's issuer not found in OCSP-embedded certs or TSL trust store (subject="
                + autCert.getIssuerX500Principal() + ")");
    }

    /**
     * RFC 6960: a {@code SingleResponse} carries {@code certStatus = GOOD | REVOKED | UNKNOWN}.
     * BouncyCastle returns {@code null} from {@link SingleResp#getCertStatus()} for GOOD; any
     * non-null value (RevokedStatus, UnknownStatus) means the cert is not currently valid for use.
     * <p>
     * The OCSP response was generated specifically for the AUT cert (bound via {@code certHash}
     * earlier), so all SingleResponses in this response refer to that cert and must all be GOOD.
     */
    private void verifyOcspCertStatusGood(BasicOCSPResp basicOcsp) {
        SingleResp[] responses = basicOcsp.getResponses();
        if (responses == null || responses.length == 0) {
            throw new A24624VerificationException("OCSP response contains no SingleResponses");
        }
        for (SingleResp sr : responses) {
            CertificateStatus status = sr.getCertStatus();
            if (status != null) {
                throw new A24624VerificationException("OCSP cert status is not GOOD: " + describeStatus(status));
            }
        }
    }

    private static String describeStatus(CertificateStatus status) {
        if (status instanceof RevokedStatus rs) {
            return "REVOKED at " + rs.getRevocationTime();
        }
        if (status instanceof UnknownStatus) {
            return "UNKNOWN";
        }
        return status.getClass().getSimpleName();
    }

    private BasicOCSPResp parseOcsp(byte[] ocspBytes) {
        if (ocspBytes == null || ocspBytes.length == 0) {
            throw new A24624VerificationException("OCSP response missing from SignedPublicVauKeys");
        }
        try {
            OCSPResp ocsp = new OCSPResp(ocspBytes);
            Object responseObject = ocsp.getResponseObject();
            if (!(responseObject instanceof BasicOCSPResp basic)) {
                throw new A24624VerificationException("OCSP response is not a BasicOCSPResponse: "
                    + (responseObject == null ? "null" : responseObject.getClass().getName()));
            }
            return basic;
        } catch (Exception e) {
            throw new A24624VerificationException("Failed to parse OCSP response", e);
        }
    }

    private void checkOcspFreshness(BasicOCSPResp basicOcsp) {
        Instant producedAt = basicOcsp.getProducedAt().toInstant();
        Duration age = Duration.between(producedAt, Instant.now());
        if (age.isNegative()) {
            throw new A24624VerificationException("OCSP producedAt is in the future: " + producedAt);
        }
        if (age.compareTo(MAX_OCSP_AGE) > 0) {
            throw new A24624VerificationException(
                "OCSP response too old: producedAt=" + producedAt + " age=" + age + " (max " + MAX_OCSP_AGE + ")");
        }
    }

    private void verifyOcspSignature(BasicOCSPResp basicOcsp, X509Certificate autCert) {
        try {
            JcaX509CertificateConverter conv = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME);
            JcaContentVerifierProviderBuilder vpBuilder = new JcaContentVerifierProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME);

            X509Certificate responderCert = null;
            for (X509CertificateHolder holder : basicOcsp.getCerts()) {
                X509Certificate candidate = conv.getCertificate(holder);
                ContentVerifierProvider vp = vpBuilder.build(candidate.getPublicKey());
                if (basicOcsp.isSignatureValid(vp)) {
                    responderCert = candidate;
                    break;
                }
            }
            if (responderCert == null) {
                throw new A24624VerificationException(
                    "OCSP response signature did not verify against any embedded cert");
            }

            authorizeOcspResponder(responderCert, autCert);
        } catch (A24624VerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new A24624VerificationException("Failed to verify OCSP response signature", e);
        }
    }

    /**
     * RFC 6960 §4.2.2.2 / Gematik TUC_PKI_006: the OCSP response signer must be either
     * <ol>
     *   <li>the CA that issued the certificate being checked (issuer-of-AUT-cert), or</li>
     *   <li>a delegated responder cert whose EKU contains {@code id-kp-OCSPSigning}.</li>
     * </ol>
     * In both cases the responder cert itself must chain to a configured trust anchor.
     */
    private void authorizeOcspResponder(X509Certificate responderCert, X509Certificate autCert) {
        boolean isIssuingCa = false;
        if (responderCert.getSubjectX500Principal().equals(autCert.getIssuerX500Principal())) {
            try {
                autCert.verify(responderCert.getPublicKey());
                isIssuingCa = true;
            } catch (Exception ignored) {
                // Subject name matched but the candidate did not actually sign autCert;
                // fall through to the delegated-responder check.
            }
        }

        if (!isIssuingCa) {
            try {
                List<String> ekus = responderCert.getExtendedKeyUsage();
                if (ekus == null || !ekus.contains(OCSP_SIGNING_EKU)) {
                    throw new A24624VerificationException(
                        "OCSP responder cert is not the issuing CA and lacks id-kp-OCSPSigning EKU");
                }
            } catch (CertificateParsingException e) {
                throw new A24624VerificationException("Failed to read OCSP responder EKU", e);
            }
        }

        chainValidate(responderCert, "OCSP responder cert");
    }

    /**
     * Validates the chain from {@code cert} up to a TI-PKI trust anchor using PKIX.
     * <p>
     * We deliberately don't use {@link X509TrustManager#checkServerTrusted} here because that
     * triggers JSSE's TLS-server endpoint check ({@code EndEntityChecker.checkTLSServer}),
     * which enforces TLS-server-specific KeyUsage and EKU constraints (e.g., requires
     * {@code digitalSignature} for ECDHE_ECDSA, {@code id-kp-serverAuth} EKU). Those checks
     * are wrong for OCSP responder certs (which carry {@code id-kp-OCSPSigning}, no
     * {@code digitalSignature}) and incidentally also wrong for VAU AUT certs (signing
     * certs, not TLS server certs).
     */
    private void chainValidate(X509Certificate cert, String purpose) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            CertPath path = cf.generateCertPath(List.of(cert));
            PKIXParameters params = new PKIXParameters(collectAnchors());
            params.setRevocationEnabled(false); // OCSP / freshness handled separately
            CertPathValidator.getInstance("PKIX").validate(path, params);
        } catch (Exception e) {
            throw new A24624VerificationException(
                purpose + " does not chain to a TI-PKI trust anchor: " + e.getMessage(), e);
        }
    }

    private Set<TrustAnchor> collectAnchors() {
        X509Certificate[] issuers = trustManager.getAcceptedIssuers();
        if (issuers == null || issuers.length == 0) {
            throw new A24624VerificationException(
                "Configured TrustManager exposes no accepted issuers — TI-PKI trust set is empty");
        }
        Set<TrustAnchor> anchors = new HashSet<>(issuers.length);
        for (X509Certificate issuer : issuers) {
            anchors.add(new TrustAnchor(issuer, null));
        }
        return anchors;
    }

    private void verifyChainToTrustAnchor(X509Certificate autCert) {
        chainValidate(autCert, "Server AUT certificate");
    }

    private void verifyTemporalValidity(X509Certificate autCert) {
        try {
            autCert.checkValidity();
        } catch (CertificateException e) {
            throw new A24624VerificationException("Server AUT certificate is not within its temporal validity window", e);
        }
    }

    private void verifyRoleOid(X509Certificate autCert) {
        byte[] admissionExt = autCert.getExtensionValue(ISISMTTObjectIdentifiers.id_isismtt_at_admission.getId());
        if (admissionExt == null) {
            throw new A24624VerificationException(
                "Server AUT certificate has no admission extension (role OID " + oidEpaVau + " required)");
        }
        try (ASN1InputStream outer = new ASN1InputStream(admissionExt)) {
            ASN1Primitive wrapper = outer.readObject();
            if (!(wrapper instanceof DEROctetString octet)) {
                throw new A24624VerificationException("Admission extension is not a DEROctetString");
            }
            try (ASN1InputStream inner = new ASN1InputStream(new ByteArrayInputStream(octet.getOctets()))) {
                AdmissionSyntax admissionSyntax = AdmissionSyntax.getInstance(inner.readObject());
                for (Admissions admissions : admissionSyntax.getContentsOfAdmissions()) {
                    for (ProfessionInfo info : admissions.getProfessionInfos()) {
                        ASN1ObjectIdentifier[] oids = info.getProfessionOIDs();
                        if (oids == null) {
                            continue;
                        }
                        for (ASN1ObjectIdentifier oid : oids) {
                            if (oidEpaVau.equals(oid.getId())) {
                                return;
                            }
                        }
                    }
                }
            }
        } catch (A24624VerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new A24624VerificationException("Failed to parse admission extension", e);
        }
        throw new A24624VerificationException(
            "Server AUT certificate does not declare role OID " + oidEpaVau + " (oid_epa_vau)");
    }

    private void verifyEs256Signature(X509Certificate autCert, SignedPublicVauKeys signedKeys) {
        if (signedKeys.getSignedPubKeys() == null || signedKeys.getSignatureEs256() == null) {
            throw new A24624VerificationException("signed_pub_keys or signature-ES256 is missing");
        }
        try {
            Signature sig = Signature.getInstance("SHA256withPLAIN-ECDSA", BouncyCastleProvider.PROVIDER_NAME);
            sig.initVerify(autCert.getPublicKey());
            sig.update(signedKeys.getSignedPubKeys());
            if (!sig.verify(signedKeys.getSignatureEs256())) {
                throw new A24624VerificationException("ES256 signature over signed_pub_keys did not verify");
            }
        } catch (A24624VerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new A24624VerificationException("Failed to verify ES256 signature over signed_pub_keys", e);
        }
    }

    private void verifyExpInFuture(SignedPublicVauKeys signedKeys) {
        VauPublicKeys pub;
        try {
            pub = signedKeys.extractVauKeys();
        } catch (RuntimeException e) {
            throw new A24624VerificationException("Failed to extract VauPublicKeys for exp check", e);
        }
        long now = Instant.now().getEpochSecond();
        if (pub.getExp() <= now) {
            throw new A24624VerificationException(
                "VAU public key has expired: exp=" + pub.getExp() + " now=" + now);
        }
    }
}
