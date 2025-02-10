package de.service.health.api.epa4all;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Set;

@ApplicationScoped
public class ServicehealthConfig {

    @ConfigProperty(name = "servicehealth.client.mask-sensitive", defaultValue = "true")
    boolean maskSensitive;

    @ConfigProperty(name = "servicehealth.client.masked-attributes")
    Set<String> maskedAttributes;

    @ConfigProperty(name = "servicehealth.client.masked-headers")
    Set<String> maskedHeaders;

    @ConfigProperty(name = "servicehealth.client.id")
    String servicehealthClientId;

    public Set<String> getMaskedAttributes() {
        return maskSensitive ? maskedAttributes : Set.of();
    }

    public Set<String> getMaskedHeaders() {
        return maskSensitive ? maskedHeaders : Set.of();
    }
}
