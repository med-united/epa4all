package de.servicehealth.epa4all.server.rest.consent;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum ConsentFunction {

    Medication("medication"),
    ErpSubmission("erp-submission");

    private final String function;

    ConsentFunction(String function) {
        this.function = function;
    }

    public static ConsentFunction from(String function) {
        return Arrays.stream(values())
            .filter(f -> f.getFunction().equalsIgnoreCase(function))
            .findFirst()
            .orElse(null);
    }
}