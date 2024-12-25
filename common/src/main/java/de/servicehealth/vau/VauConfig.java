package de.servicehealth.vau;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class VauConfig {

    @ConfigProperty(name = "epa.vau.pu", defaultValue = "false")
    boolean pu;

    @ConfigProperty(name = "epa.vau.mock", defaultValue = "false")
    boolean mock;

    @ConfigProperty(name = "epa.vau.tracing.enabled", defaultValue = "true")
    boolean tracingEnabled;

    @ConfigProperty(name = "epa.vau.pool.size", defaultValue = "10")
    int vauPoolSize;

    @ConfigProperty(name = "epa.vau.read.timeout.sec", defaultValue = "30")
    int vauReadTimeoutSec;

    @ConfigProperty(name = "epa.vau.connection.timeout.ms", defaultValue = "10000")
    int connectionTimeoutMs;

    @ConfigProperty(name = "epa.vau.request.timeout.ms", defaultValue = "60000")
    int requestTimeoutMs;
}
