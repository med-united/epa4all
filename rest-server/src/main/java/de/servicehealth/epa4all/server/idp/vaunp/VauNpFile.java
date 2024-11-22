package de.servicehealth.epa4all.server.idp.vaunp;

import de.servicehealth.epa4all.server.file.MapDumpFile;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class VauNpFile extends MapDumpFile<VauNpKey, String> {

    private static final String VAU_NP_FILE_NAME = "vau-np";

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
        String konnektor = parts[0].trim();
        String epaBackend = parts[1].trim();
        String vauNp = parts[2].trim();
        return Pair.of(new VauNpKey(konnektor, epaBackend), vauNp);
    }

    @Override
    protected String serialize(Map.Entry<VauNpKey, String> entry) {
        VauNpKey vauNpKey = entry.getKey();
        return vauNpKey.getKonnektor() + "_" + vauNpKey.getEpaBackend() + "_" + entry.getValue();
    }
}
