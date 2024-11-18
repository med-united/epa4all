package de.servicehealth.epa4all.server.filetracker;

import com.google.common.io.Files;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EntitlementFile {

    private static final Logger log = Logger.getLogger(EntitlementFile.class.getName());

    private static final String ENTITLEMENT_FILE = "entitlement-expiry";

    private final ReentrantReadWriteLock lock;
    private final String insurantId;
    private final File file;

    public EntitlementFile(File insurantLocalFolder, String insurantId) throws IOException {
        this.insurantId = insurantId;
        lock = new ReentrantReadWriteLock();
        file = new File(insurantLocalFolder, ENTITLEMENT_FILE);
        if (!file.exists()) {
            boolean created = file.createNewFile();
            if (!created) {
                String msg = String.format("Could not create %s for %s", ENTITLEMENT_FILE, insurantId);
                throw new IllegalStateException(msg);
            }
        }
    }

    private Map<String, Instant> getEntitlementsMap() throws IOException {
        return Files.readLines(file, StandardCharsets.ISO_8859_1)
            .stream()
            .map(s -> {
                String[] parts = s.split("_");
                String insurantId = parts[0].trim();
                Instant validTo = Instant.parse(parts[1].trim());
                return Pair.of(insurantId, validTo);
            })
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    public Instant getEntitlement() throws IOException {
        lock.readLock().lock();
        try {
            return getEntitlementsMap().get(insurantId);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void updateEntitlement(Instant validTo) {
        lock.writeLock().lock();
        try {
            Map<String, Instant> entitlementsMap = getEntitlementsMap();
            entitlementsMap.put(insurantId, validTo);
            String content = entitlementsMap.entrySet()
                .stream().map(e -> e.getKey() + "_" + e.getValue())
                .collect(Collectors.joining("\n"));
            try (FileOutputStream os = new FileOutputStream(file)) {
                os.write(content.getBytes());
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unable to update 'entitlement-expiry' file for " + insurantId);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
