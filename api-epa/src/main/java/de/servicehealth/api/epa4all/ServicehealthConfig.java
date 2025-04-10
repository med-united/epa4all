package de.servicehealth.api.epa4all;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Set;

@Getter
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

    public Set<String> getSafeMaskedAttributes() {
        return isMaskSensitive() ? getMaskedAttributes() : Set.of();
    }

    public Set<String> getSafeMaskedHeaders() {
        return isMaskSensitive() ? getMaskedHeaders() : Set.of();
    }
}
