package de.servicehealth.epa4all.server.file;

import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static de.health.service.cetp.konnektorconfig.FSConfigService.CONFIG_DELIMETER;

public abstract class StringKeyValueFile extends MapDumpFile<String, String> {

    public StringKeyValueFile(File folder) throws IOException {
        super(folder);
    }

    @Override
    protected Pair<String, String> deserialize(String line) {
        String[] parts = line.split(CONFIG_DELIMETER);
        String key = parts[0].trim();
        String value = parts[1].trim();
        return Pair.of(key, value);
    }

    @Override
    protected String serialize(Map.Entry<String, String> entry) {
        return String.join(CONFIG_DELIMETER, entry.getKey(), entry.getValue());
    }
}