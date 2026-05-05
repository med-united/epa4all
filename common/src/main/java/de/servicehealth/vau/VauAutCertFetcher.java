package de.servicehealth.vau;

import java.security.cert.X509Certificate;

/**
 * Per-{@link VauClient} fetcher for the server-side AUT certificate. The backend identifier
 * is captured by the construction site (typically {@code VauFacade}) so the verification path
 * only needs the {@code certHash}/{@code cdv} extracted from the M2 payload.
 */
@FunctionalInterface
public interface VauAutCertFetcher {

    X509Certificate fetch(byte[] certHash, int cdv);
}
