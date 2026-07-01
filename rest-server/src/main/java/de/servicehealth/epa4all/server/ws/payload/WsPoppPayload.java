package de.servicehealth.epa4all.server.ws.payload;

import jakarta.json.bind.annotation.JsonbProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WsPoppPayload {

    @JsonbProperty("cardTerminalId")
    String cardTerminalId;

    @JsonbProperty("telematikId")
    String telematikId;

    @JsonbProperty("cetpXml")
    String cetpXml;

    @JsonbProperty("readVSDResponseXml")
    String readVSDResponseXml;

    @JsonbProperty("poppToken")
    String poppToken;

}
