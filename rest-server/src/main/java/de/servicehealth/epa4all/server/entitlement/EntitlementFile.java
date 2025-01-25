package de.servicehealth.epa4all.server.entitlement;

import de.servicehealth.epa4all.server.file.MapDumpFile;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class EntitlementFile extends MapDumpFile<String, Instant> {

    public static final String ENTITLEMENT_FILE = "entitlement-expiry";

    private final static ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final String insurantId;

    public EntitlementFile(File folder, String insurantId) throws IOException {
        super(folder);
        this.insurantId = insurantId;
    }

    @Override
    protected String getFileName() {
        return ENTITLEMENT_FILE;
    }

    @Override
    protected Pair<String, Instant> deserialize(String line) {
        String[] parts = line.split("_");
        String insurantId = parts[0].trim();
        Instant validTo = Instant.parse(parts[1].trim());
        return Pair.of(insurantId, validTo);
    }

    @Override
    protected String serialize(Map.Entry<String, Instant> entry) {
        return entry.getKey() + "_" + entry.getValue();
    }

    public Instant getEntitlement() {
        return get().get(insurantId);
    }

    @Override
    public Map<String, Instant> get() {
        lock.readLock().lock();
        try {
            return load();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void update(Map<String, Instant> entitlementsMap) {
        lock.writeLock().lock();
        try {
            store(entitlementsMap);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void reset() {
        lock.writeLock().lock();
        try {
            store(Map.of());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateEntitlement(Instant validTo) {
        lock.writeLock().lock();
        try {
            Map<String, Instant> entitlementsMap = load();
            entitlementsMap.put(insurantId, validTo);
            store(entitlementsMap);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
