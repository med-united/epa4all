package de.servicehealth.vau;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class VauConfig {

    @ConfigProperty(name = "epa.vau.pool.size", defaultValue = "10")
    int vauPoolSize;

    @ConfigProperty(name = "epa.vau.read.timeout.sec", defaultValue = "10")
    int vauReadTimeoutSec;
}
