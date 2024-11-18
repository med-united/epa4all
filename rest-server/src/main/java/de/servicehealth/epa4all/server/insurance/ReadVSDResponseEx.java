package de.servicehealth.epa4all.server.insurance;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReadVSDResponseEx {

    private String telematikId;
    private String insurantId;
    private ReadVSDResponse readVSDResponse;
}
