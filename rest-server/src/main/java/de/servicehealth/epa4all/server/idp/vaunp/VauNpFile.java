package de.servicehealth.epa4all.server.idp.vaunp;

import de.servicehealth.epa4all.server.file.MapDumpFile;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.util.Map;

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
        String[] parts = line.split("_");
        String smcbHandle = parts[0].trim();
        String konnektor = parts[1].trim();
        String epaBackend = parts[2].trim();
        String vauNp = parts[3].trim();
        return Pair.of(new VauNpKey(smcbHandle, konnektor, epaBackend), vauNp);
    }

    @Override
    protected String serialize(Map.Entry<VauNpKey, String> entry) {
        VauNpKey key = entry.getKey();
        return key.getSmcbHandle() + "_" + key.getKonnektor() + "_" + key.getEpaBackend() + "_" + entry.getValue();
    }

    public void update(Map<VauNpKey, String> vauNpMap) {
        Map<VauNpKey, String> cachedMap = get();
        cachedMap.putAll(vauNpMap);
        store(cachedMap);
    }
}
