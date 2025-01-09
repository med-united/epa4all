package de.servicehealth.epa4all.server.ws;

import jakarta.json.bind.annotation.JsonbProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CashierPayload {

    @JsonbProperty("cardTerminalId")
    String cardTerminalId;

    @JsonbProperty("telematikId")
    String telematikId;

    @JsonbProperty("kvnr")
    String kvnr;

    @JsonbProperty("medicationPdfBase64")
    String medicationPdfBase64;
}
