package de.servicehealth.epa4all.server.popp;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class PoppConfig {

    @ConfigProperty(name = "popp.authorization.filter.enabled", defaultValue = "false")
    boolean poppAuthorizationFilterEnabled;
}
