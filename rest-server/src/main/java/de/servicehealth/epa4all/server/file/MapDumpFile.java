package de.servicehealth.epa4all.server.file;

import com.google.common.io.Files;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

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
        return Map.of();
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
        writeLock(Map.of(), this::store);
    }

    public void update(Map<K, V> map) {
        writeLock(map, this::store);
    }

    protected abstract String getFileName();
    protected abstract Pair<K, V> deserialize(String line);
    protected abstract String serialize(Map.Entry<K, V> entry);

    protected Map<K, V> load() throws Exception {
        return Files.readLines(file, ISO_8859_1)
            .stream()
            .map(this::deserialize)
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    protected void store(Map<K, V> vauNpMap) {
        try {
            String content = vauNpMap.entrySet()
                .stream().map(this::serialize)
                .collect(Collectors.joining("\n"));
            try (FileOutputStream os = new FileOutputStream(file)) {
                os.write(content.getBytes());
            }
        } catch (Exception e) {
            log.error(String.format("Unable to store '%s' file", getFileName()));
        }
    }
}
