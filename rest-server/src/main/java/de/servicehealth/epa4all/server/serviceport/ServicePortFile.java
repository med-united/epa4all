package de.servicehealth.epa4all.server.serviceport;

import com.google.common.io.Files;
import de.servicehealth.epa4all.server.file.MapDumpFile;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import static de.health.service.cetp.konnektorconfig.FSConfigService.CONFIG_DELIMETER;

public class ServicePortFile extends MapDumpFile<String, Map<String, String>> {

    private static final String SERVICE_PORTS_FILE_NAME = "service-ports";

    public ServicePortFile(File configFolder) throws IOException {
        super(configFolder);
    }

    @Override
    protected String getFileName() {
        return SERVICE_PORTS_FILE_NAME;
    }

    @Override
    protected Pair<String, Map<String, String>> deserialize(String line) {
        String[] parts = line.split(CONFIG_DELIMETER);
        return Pair.of(parts[0], Map.of(parts[1], parts[2]));
    }

    @Override
    protected Map<String, Map<String, String>> load() throws Exception {
        return Files.readLines(file, StandardCharsets.ISO_8859_1)
            .stream()
            .map(line -> {
                String[] parts = line.split(CONFIG_DELIMETER);
                String konnektorUrl = parts[0];
                String addressType = parts[1];
                String endpoint = parts[2];

                return Pair.of(konnektorUrl, Pair.of(addressType, endpoint));
            })
            .collect(Collectors.groupingBy(Pair::getKey, Collectors.toMap(
                pair -> pair.getValue().getKey(),
                pair -> pair.getValue().getValue()
            )));
    }

    @Override
    protected String serialize(Map.Entry<String, Map<String, String>> entry) {
        String konnektorUrl = entry.getKey();
        return entry.getValue().entrySet()
            .stream()
            .map(e -> String.join(CONFIG_DELIMETER, konnektorUrl, e.getKey(), e.getValue()))
            .collect(Collectors.joining("\n"));
    }
}
