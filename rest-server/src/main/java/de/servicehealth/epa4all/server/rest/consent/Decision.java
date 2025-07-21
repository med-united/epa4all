package de.servicehealth.epa4all.server.rest.consent;

public record Decision(ConsentFunction consentFunction, boolean permitted) {
}