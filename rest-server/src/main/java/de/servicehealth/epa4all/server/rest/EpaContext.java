package de.servicehealth.epa4all.server.rest;

import de.servicehealth.epa4all.server.insurance.InsuranceData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

@Getter
@AllArgsConstructor
@ToString
public class EpaContext {

    private InsuranceData insuranceData;
    private Map<String, Object> runtimeAttributes;
}
