package de.servicehealth.epa4all.server.file;

import com.google.common.io.Files;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static de.servicehealth.utils.ServerUtils.writeBytesToFile;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.stream.Collectors.toMap;

public abstract class MapDumpFile<K, V> {

    protected static final Logger log = LoggerFactory.getLogger(MapDumpFile.class.getName());
    private static final Map<Class<?>, ReentrantReadWriteLock> filesLocks = new ConcurrentHashMap<>();

    protected final File file;

    public MapDumpFile(File folder) throws IOException {
        filesLocks.putIfAbsent(getClass(), new ReentrantReadWriteLock(true));

        String fileName = getFileName();
        file = new File(folder, fileName);
        if (!file.exists()) {
            log.info(String.format("Creating '%s' in the folder '%s'", fileName, folder.getAbsolutePath()));
            file.createNewFile();
        }
    }

    public Map<K, V> get() {
        filesLocks.get(getClass()).readLock().lock();
        try {
            return load();
        } catch (Exception e) {
            log.error(String.format("Unable to load '%s' file, resetting", getFileName()));
        } finally {
            filesLocks.get(getClass()).readLock().unlock();
        }
        reset();
        return new HashMap<>();
    }

    protected interface MapAction<K, V> {
        void execute(Map<K, V> map);
    }

    protected void writeLock(Map<K, V> map, MapAction<K, V> action) {
        filesLocks.get(getClass()).writeLock().lock();
        try {
            action.execute(map);
        } finally {
            filesLocks.get(getClass()).writeLock().unlock();
        }
    }

    public void reset() {
        writeLock(new HashMap<>(), this::store);
    }

    public Map<K, V> overwrite(Predicate<? super Map.Entry<K, V>> predicate) throws Exception {
        filesLocks.get(getClass()).writeLock().lock();
        try {
            Map<K, V> current = load();
            Map<K, V> filtered = current.entrySet().stream()
                .filter(predicate)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            if (current.size() > filtered.size()) {
                store(filtered);
            }
            return filtered;
        } finally {
            filesLocks.get(getClass()).writeLock().unlock();
        }
    }

    public void overwrite(Map<K, V> map) {
        writeLock(map, this::store);
    }

    public void put(K key, V value) {
        filesLocks.get(getClass()).writeLock().lock();
        try {
            Map<K, V> map;
            try {
                map = load();
            } catch (Exception e) {
                map = new HashMap<>();
            }
            map.put(key, value);
            store(map);
        } finally {
            filesLocks.get(getClass()).writeLock().unlock();
        }
    }

    protected abstract String getFileName();

    protected abstract Pair<K, V> deserialize(String line);

    protected abstract String serialize(Map.Entry<K, V> entry);

    protected Map<K, V> load() throws Exception {
        return Files.readLines(file, ISO_8859_1)
            .stream()
            .map(this::deserialize)
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    protected void store(Map<K, V> vauNpMap) {
        try {
            String content = vauNpMap.entrySet().stream()
                .map(this::serialize)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));

            writeBytesToFile(content.getBytes(), file);
        } catch (Exception e) {
            log.error(String.format("Unable to store '%s' file", getFileName()));
        }
    }
}