package de.servicehealth.epa4all.common;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.security.KeyStore;

/**
 * Loader for the bundled non-TSL test trust store ({@code test-truststore/test-truststore.p12})
 * that contains wiremock's self-signed CA. Used by tests that previously relied on
 * {@code SSLUtils.getFakeTrustManagers()} (now removed) to validate TLS to wiremock servers
 * and to validate client certs presented to in-memory test servers (e.g.
 * {@code com.unboundid.ldap.listener.InMemoryDirectoryServer}).
 * <p>
 * Centralised here so the same store is used by every module — keeps the trust set tiny and
 * eliminates trust-all paths from the codebase.
 */
public final class TestTrustStore {

    public static final String RESOURCE = "test-truststore/test-truststore.p12";
    public static final String PASSWORD = "password";
    public static final String TYPE = "PKCS12";

    private TestTrustStore() {
    }

    public static KeyStore load() throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " not on classpath (depend on common-test in test scope)");
            }
            KeyStore ks = KeyStore.getInstance(TYPE);
            ks.load(in, PASSWORD.toCharArray());
            return ks;
        }
    }

    public static X509TrustManager trustManager() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(load());
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new IllegalStateException("TrustManagerFactory produced no X509TrustManager from " + RESOURCE);
    }
}
