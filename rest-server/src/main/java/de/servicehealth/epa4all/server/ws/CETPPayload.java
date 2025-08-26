package de.servicehealth.epa4all.server.ws;

import jakarta.json.bind.annotation.JsonbProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CETPPayload {

    @JsonbProperty("smcbHandle")
    String smcbHandle;

    @JsonbProperty("telematikId")
    String telematikId;

    @JsonbProperty("kvnr")
    String kvnr;

    @JsonbProperty("error")
    String error;

    @JsonbProperty("parameters")
    Map<String, String> parameters;
}