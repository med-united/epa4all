package de.servicehealth.gematik;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * CBOR payload returned by the VAU CertData endpoint per gemSpec_Krypt A_24957.
 * <p>
 * Mirrors the structure documented in med-united/lib-vau commit f335017 and addressed by
 * {@code GET https://<vau-host>/CertData.<hex(cert_hash)>-<cdv>}:
 * <ul>
 *   <li>{@link #cert} — DER-encoded server AUT-VAU certificate (the cert that signed
 *       {@code signed_pub_keys} in M2).</li>
 *   <li>{@link #ca} — DER-encoded Komponenten-CA that issued {@link #cert}.</li>
 *   <li>{@link #rca_chain} — array of DER-encoded cross-certificates linking the CA up to
 *       the TSL trust anchors (used when the issuing CA is not yet in the in-memory TSL).</li>
 * </ul>
 */
@Getter
public class VauAutCertificateWithChain {

    @JsonProperty("cert")
    private byte[] cert;

    @JsonProperty("ca")
    private byte[] ca;

    @JsonProperty("rca_chain")
    private byte[][] rca_chain;
}
