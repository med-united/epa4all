package de.servicehealth.epa4all.server.rest;

import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.logging.LogField;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.toMap;

@Getter
@AllArgsConstructor
@ToString
public class EpaContext {

    private String insurantId;
    private String backend;
    private Instant entitlementExpiry;
    private InsuranceData insuranceData;
    private Map<String, String> headers;

    public Map<String, String> getXHeaders() {
        return headers;
    }

    public Map<LogField, String> getMdcMap() {
        return headers.entrySet().stream()
            .map(e -> {
                LogField logField = LogField.from(e.getKey());
                if (logField == null) {
                    return null;
                }
                return Pair.of(logField, e.getValue());
            })
            .filter(Objects::nonNull)
            .collect(toMap(Pair::getKey, Pair::getValue));
    }
}
