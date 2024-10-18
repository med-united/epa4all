package de.servicehealth.epa4all.medication.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class EpaMedicationConfig {

    @ConfigProperty(name = "epa.is-vau-proxy", defaultValue = "true")
    Boolean proxy;

    @ConfigProperty(name = "medication-service.api.url")
    String medicationServiceApiUrl;

    @ConfigProperty(name = "medication-service.render.url")
    String medicationServiceRenderUrl;

    public boolean isProxy() {
        return proxy;
    }
}
