package de.servicehealth.epa4all.server.file;

import com.google.common.io.Files;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class MapDumpFile<K, V> {

    private static final Logger log = Logger.getLogger(MapDumpFile.class.getName());

    protected final File file;

    public MapDumpFile(File folder) throws IOException {
        String fileName = getFileName();
        file = new File(folder, fileName);
        if (!file.exists()) {
            log.info(String.format("Creating '%s' in the folder '%s'", fileName, folder.getAbsolutePath()));
            file.createNewFile();
        }
    }

    protected abstract String getFileName();
    protected abstract Pair<K, V> deserialize(String line);
    protected abstract String serialize(Map.Entry<K, V> entry);

    protected Map<K, V> load() {
        try {
            return Files.readLines(file, StandardCharsets.ISO_8859_1)
                .stream()
                .map(this::deserialize)
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Unable to load '%s' file", getFileName()));
            return Map.of();
        }
    }

    public Map<K, V> get() {
        return load();
    }

    public void store(Map<K, V> vauNpMap) {
        try {
            String content = vauNpMap.entrySet()
                .stream().map(this::serialize)
                .collect(Collectors.joining("\n"));
            try (FileOutputStream os = new FileOutputStream(file)) {
                os.write(content.getBytes());
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Unable to update '%s' file", getFileName()));
        }
    }

    public void reset() {
        store(Map.of());
    }
}
