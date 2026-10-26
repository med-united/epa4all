package de.servicehealth.gematik;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link X509ExtendedTrustManager} whose delegate can be atomically replaced at runtime.
 * <p>
 * Plug a single instance into {@code SSLContext.init(km, new TrustManager[]{ this }, null)};
 * subsequent TLS handshakes pick up new trust anchors without an SSLContext rebuild.
 * <p>
 * When a delegate {@code checkServerTrusted} call rejects the peer chain, a reload is
 * scheduled on a dedicated daemon thread (the original {@code CertificateException} is
 * still thrown back to JSSE so the failing handshake is not silently masked). The reload
 * is rate-limited and re-entrancy is blocked, so a burst of failing handshakes triggers
 * at most one TSL fetch per {@code minReloadInterval}.
 * <p>
 * Must extend {@link X509ExtendedTrustManager} (not the basic {@code X509TrustManager});
 * the basic interface causes JSSE to wrap the manager in {@code AbstractTrustManagerWrapper},
 * which silently disables endpoint identification.
 */
public final class ReloadableX509TrustManager extends X509ExtendedTrustManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReloadableX509TrustManager.class);

    public interface TrustStoreLoader {
        /** Load the trust store. Implementations may serve from a cache. */
        KeyStore load() throws Exception;

        /**
         * Invalidate any cached state so the next {@link #load()} pulls fresh data.
         * Called before every reload triggered by a handshake failure. Default is a no-op.
         */
        default void invalidate() {}
    }

    private final AtomicReference<X509ExtendedTrustManager> delegate;
    private final TrustStoreLoader loader;
    private final long minIntervalMs;
    private final ExecutorService reloadExecutor;
    private final AtomicBoolean reloading = new AtomicBoolean(false);
    private final AtomicLong lastReloadAtMs = new AtomicLong(0);

    public ReloadableX509TrustManager(TrustStoreLoader loader, Duration minReloadInterval) throws Exception {
        if (loader == null) {
            throw new IllegalArgumentException("loader must not be null");
        }
        if (minReloadInterval == null || minReloadInterval.isNegative()) {
            throw new IllegalArgumentException("minReloadInterval must be non-negative");
        }
        this.loader = loader;
        this.minIntervalMs = minReloadInterval.toMillis();
        this.delegate = new AtomicReference<>(extract(loader.load()));
        this.lastReloadAtMs.set(System.currentTimeMillis());
        this.reloadExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "gematik-trust-reload");
            t.setDaemon(true);
            return t;
        });
    }

    public void requestReload() {
        triggerReload();
    }

    public void reload(X509ExtendedTrustManager next) {
        if (next == null) {
            throw new IllegalArgumentException("next delegate must not be null");
        }
        X509ExtendedTrustManager prev = delegate.getAndSet(next);
        lastReloadAtMs.set(System.currentTimeMillis());
        log.info(
            "Reloaded trust anchors: {} -> {} accepted issuers",
            prev.getAcceptedIssuers().length,
            next.getAcceptedIssuers().length
        );
    }

    public void reload(KeyStore trustStore) throws KeyStoreException, NoSuchAlgorithmException {
        reload(extract(trustStore));
    }

    public static X509ExtendedTrustManager extract(KeyStore trustStore)
        throws KeyStoreException, NoSuchAlgorithmException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509ExtendedTrustManager x) {
                return x;
            }
        }
        throw new KeyStoreException(
            "No X509ExtendedTrustManager produced by " + TrustManagerFactory.getDefaultAlgorithm()
        );
    }

    @Override
    public void close() {
        reloadExecutor.shutdown();
        try {
            if (!reloadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                reloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reloadExecutor.shutdownNow();
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        delegate.get().checkClientTrusted(chain, authType);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        delegate.get().checkClientTrusted(chain, authType, socket);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        delegate.get().checkClientTrusted(chain, authType, engine);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            delegate.get().checkServerTrusted(withIssuerChain(chain), authType);
        } catch (CertificateException ex) {
            maybeTriggerReload(ex);
            throw ex;
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        try {
            delegate.get().checkServerTrusted(withIssuerChain(chain), authType, socket);
        } catch (CertificateException ex) {
            maybeTriggerReload(ex);
            throw ex;
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        try {
            delegate.get().checkServerTrusted(withIssuerChain(chain), authType, engine);
        } catch (CertificateException ex) {
            maybeTriggerReload(ex);
            throw ex;
        }
    }

    /**
     * Completes a peer chain whose issuer certificates are missing, using our own trust
     * anchors. TI components (e.g. the ePA Aktensystem) commonly send only their leaf
     * certificate, and the matching Komponenten-CA from the TSL is an intermediate, not a
     * root. With BouncyCastle registered as the highest-priority provider (see IdpClient),
     * JSSE's PKIX path building resolves to BC's CertPathBuilder, which locates issuers only
     * in the presented chain — never among the trust anchors — so leaf-only chains fail with
     * "No issuer certificate for certificate in certification path found". Appending the
     * verified issuer(s) from the anchor set keeps validation semantics intact (the delegate
     * still performs full PKIX checks) while making path building succeed.
     */
    private X509Certificate[] withIssuerChain(X509Certificate[] chain) {
        if (chain == null || chain.length == 0) {
            return chain;
        }
        List<X509Certificate> extended = new ArrayList<>(Arrays.asList(chain));
        X509Certificate last = extended.get(extended.size() - 1);
        X509Certificate[] anchors = delegate.get().getAcceptedIssuers();
        for (int depth = 0; depth < 6; depth++) {
            if (last.getSubjectX500Principal().equals(last.getIssuerX500Principal())) {
                break;
            }
            X509Certificate issuer = null;
            for (X509Certificate anchor : anchors) {
                if (anchor.getSubjectX500Principal().equals(last.getIssuerX500Principal())) {
                    try {
                        last.verify(anchor.getPublicKey());
                        issuer = anchor;
                        break;
                    } catch (Exception ignored) {
                        // same subject DN but different key (CA re-key) — keep looking
                    }
                }
            }
            if (issuer == null || extended.contains(issuer)) {
                break;
            }
            extended.add(issuer);
            last = issuer;
        }
        return extended.toArray(new X509Certificate[0]);
    }

    private void maybeTriggerReload(CertificateException ex) {
        if (isUntrustedAnchor(ex)) {
            triggerReload();
        }
    }

    /**
     * Walks the cause chain looking for {@link CertPathBuilderException} — JSSE's signal that
     * no trust anchor could be found for the peer chain. Other failures (expired leaf, hostname
     * mismatch, revoked leaf) are not anchor problems and reloading the TSL would not fix them.
     */
    private static boolean isUntrustedAnchor(Throwable t) {
        for (int i = 0; t != null && i < 16; i++) {
            if (t instanceof CertPathBuilderException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegate.get().getAcceptedIssuers();
    }

    private void triggerReload() {
        long since = System.currentTimeMillis() - lastReloadAtMs.get();
        if (since < minIntervalMs) {
            return;
        }
        if (!reloading.compareAndSet(false, true)) {
            return;
        }
        if (reloadExecutor.isShutdown()) {
            reloading.set(false);
            return;
        }
        reloadExecutor.submit(() -> {
            try {
                loader.invalidate();
                reload(loader.load());
            } catch (Exception e) {
                log.error("Trust anchor reload failed", e);
            } finally {
                reloading.set(false);
            }
        });
    }
}
