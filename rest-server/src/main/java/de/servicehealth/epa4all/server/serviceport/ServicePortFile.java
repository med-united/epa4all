package de.servicehealth.epa4all.server.serviceport;

import com.google.common.io.Files;
import de.servicehealth.epa4all.server.file.MapDumpFile;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpFile;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ServicePortFile extends MapDumpFile<String, Map<String, String>> {

    private static final Logger log = Logger.getLogger(VauNpFile.class.getName());

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
        String[] parts = line.split("_");
        return Pair.of(parts[0], Map.of(parts[1], parts[2]));
    }

    @Override
    protected Map<String, Map<String, String>> load() {
        try {
            return Files.readLines(file, StandardCharsets.ISO_8859_1)
                .stream()
                .map(line -> {
                    String[] parts = line.split("_");
                    String konnektorUrl = parts[0];
                    String addressType = parts[1];
                    String endpoint = parts[2];

                    return Pair.of(konnektorUrl, Pair.of(addressType, endpoint));
                })
                .collect(Collectors.groupingBy(Pair::getKey, Collectors.toMap(
                    pair -> pair.getValue().getKey(),
                    pair -> pair.getValue().getValue()
                )));
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Unable to load '%s' file", getFileName()));
            return Map.of();
        }
    }

    @Override
    protected String serialize(Map.Entry<String, Map<String, String>> entry) {
        String konnektorUrl = entry.getKey();
        return entry.getValue().entrySet()
            .stream()
            .map(e -> konnektorUrl + "_" + e.getKey() + "_" + e.getValue())
            .collect(Collectors.joining("\n"));
    }
}
