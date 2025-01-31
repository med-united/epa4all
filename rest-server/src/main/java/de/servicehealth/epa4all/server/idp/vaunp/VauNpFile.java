package de.servicehealth.epa4all.server.idp.vaunp;

import de.servicehealth.epa4all.server.file.MapDumpFile;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static de.health.service.cetp.konnektorconfig.FSConfigService.CONFIG_DELIMETER;

public class VauNpFile extends MapDumpFile<VauNpKey, String> {

    public static final String VAU_NP_FILE_NAME = "vau-np";

    public VauNpFile(File configFolder) throws IOException {
        super(configFolder);
    }

    @Override
    protected String getFileName() {
        return VAU_NP_FILE_NAME;
    }

    @Override
    protected Pair<VauNpKey, String> deserialize(String line) {
        String[] parts = line.split(CONFIG_DELIMETER);
        String smcbHandle = parts[0].trim();
        String konnektor = parts[1].trim();
        String workplaceId = parts[2].trim();
        String epaBackend = parts[3].trim();
        String vauNp = parts[4].trim();
        return Pair.of(new VauNpKey(smcbHandle, konnektor, workplaceId, epaBackend), vauNp);
    }

    @Override
    protected String serialize(Map.Entry<VauNpKey, String> entry) {
        VauNpKey key = entry.getKey();
        return String.join(
            CONFIG_DELIMETER,
            key.getSmcbHandle(), key.getKonnektor(), key.getWorkplaceId(), key.getEpaBackend(), entry.getValue()
        );
    }

    @Override
    public void update(Map<VauNpKey, String> vauMap) {
        Map<VauNpKey, String> map = get();
        map.putAll(vauMap);
        writeLock(map, this::store);
    }
}
