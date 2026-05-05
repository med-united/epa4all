package de.servicehealth.gematik;

import java.security.cert.X509Certificate;

/**
 * Supplies the server-side AUT certificate that signed {@code signed_pub_keys} in a VAU M2
 * payload, so {@link A24624Verifier#verify} can validate it.
 * <p>
 * The cert is not present anywhere in the M2 payload — Gematik's OCSP responses don't embed
 * the leaf either. It is fetched per gemSpec_ePA_FdV §6.1.3 from the VAU server's CertData
 * endpoint, addressed by {@code certHash} and {@code cdv} from the M2 payload:
 * <pre>{@code
 * GET https://<backend>/CertData-<hex(certHash)>-<cdv>
 * }</pre>
 * Implementations should cache the result by {@code (backend, certHash, cdv)} for the cert's
 * lifetime; rotation surfaces as a new {@code certHash}.
 * <p>
 * Returning {@code null} causes {@link A24624Verifier} to throw — verification is fail-closed,
 * not silently skipped.
 */
public interface VauAutCertSupplier {

    X509Certificate get(String backend, byte[] certHash, int cdv);
}
