package de.servicehealth.gematik;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Enumeration;

/**
 * {@link ReloadableX509TrustManager.TrustStoreLoader} that boots from a cached PKCS#12
 * keystore on disk and falls through to a fresh TSL fetch (via {@link GematikKeyStoreProvider})
 * when the cache is missing, corrupt, or invalidated.
 * <p>
 * Workflow:
 * <ol>
 *   <li>First boot — no cache file → download via {@link GematikKeyStoreProvider}, then write
 *       all extracted trusted-cert entries to the cache file (atomic rename).</li>
 *   <li>Subsequent boots — load the cache file directly, no network.</li>
 *   <li>{@link #invalidate()} — called before every {@link ReloadableX509TrustManager} reload
 *       triggered by a handshake failure. Deletes the cache file so the next {@link #load()}
 *       fetches fresh and rewrites the cache.</li>
 * </ol>
 * The cache holds only public certificates (trusted-cert entries) — no private keys — so the
 * keystore password is a fixed, non-secret integrity HMAC.
 */
public class CachingGematikLoader implements ReloadableX509TrustManager.TrustStoreLoader {

    private static final Logger log = LoggerFactory.getLogger(CachingGematikLoader.class);

    /** Fixed integrity-only password for the public-cert cache (no secrets stored). */
    private static final char[] CACHE_PASSWORD = "epa4all-tsl-cache".toCharArray();
    private static final String CACHE_TYPE = "PKCS12";

    private final GematikEnvironment environment;
    private final Path cacheFile;

    public CachingGematikLoader(GematikEnvironment environment, Path cacheFile) {
        this.environment = environment;
        this.cacheFile = cacheFile;
    }

    @Override
    public KeyStore load() throws Exception {
        if (Files.isRegularFile(cacheFile)) {
            try {
                KeyStore cached = readCache();
                log.info("Loaded TSL truststore from cache {} ({} entries)", cacheFile, cached.size());
                return cached;
            } catch (Exception e) {
                log.warn("Failed to read TSL truststore cache {} — will refetch from Gematik. Cause: {}",
                    cacheFile, e.getMessage());
                deleteCache();
            }
        }
        KeyStore fresh = fetchFresh();
        writeCache(fresh);
        return fresh;
    }

    @Override
    public void invalidate() {
        deleteCache();
    }

    private KeyStore fetchFresh() throws Exception {
        log.info("Fetching fresh TSL truststore for environment {}", environment);
        KeyStore ks = KeyStore.getInstance(GematikKeyStoreProvider.KEYSTORE_TYPE,
            new GematikKeyStoreProvider(environment));
        ks.load(null, null);
        return ks;
    }

    private KeyStore readCache() throws Exception {
        KeyStore ks = KeyStore.getInstance(CACHE_TYPE);
        try (InputStream in = Files.newInputStream(cacheFile)) {
            ks.load(in, CACHE_PASSWORD);
        }
        return ks;
    }

    private void writeCache(KeyStore source) {
        try {
            KeyStore out = KeyStore.getInstance(CACHE_TYPE);
            out.load(null, CACHE_PASSWORD);

            Enumeration<String> aliases = source.aliases();
            int copied = 0;
            for (String alias : Collections.list(aliases)) {
                if (source.isCertificateEntry(alias)) {
                    X509Certificate cert = (X509Certificate) source.getCertificate(alias);
                    out.setCertificateEntry(alias, cert);
                    copied++;
                }
            }

            Files.createDirectories(cacheFile.getParent());
            Path tmp = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
            try (OutputStream os = Files.newOutputStream(tmp)) {
                out.store(os, CACHE_PASSWORD);
            }
            Files.move(tmp, cacheFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.info("Wrote {} TSL trust anchors to cache {}", copied, cacheFile);
        } catch (Exception e) {
            log.warn("Failed to write TSL truststore cache to {}: {} — continuing without persistence",
                cacheFile, e.getMessage());
        }
    }

    private void deleteCache() {
        try {
            if (Files.deleteIfExists(cacheFile)) {
                log.info("Deleted TSL truststore cache {}", cacheFile);
            }
        } catch (IOException e) {
            log.warn("Failed to delete TSL truststore cache {}: {}", cacheFile, e.getMessage());
        }
    }
}
