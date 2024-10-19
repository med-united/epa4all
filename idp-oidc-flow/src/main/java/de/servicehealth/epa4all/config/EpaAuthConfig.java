package de.servicehealth.epa4all.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class EpaAuthConfig {

    @ConfigProperty(name = "epa.is-vau-proxy", defaultValue = "true")
    Boolean proxy;

    @ConfigProperty(name = "authorization-service.url")
    String authorizationServiceUrl;

    public boolean isProxy() {
        return proxy;
    }
}
