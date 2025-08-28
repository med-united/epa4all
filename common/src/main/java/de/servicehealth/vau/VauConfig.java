package de.servicehealth.vau;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

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

    @ConfigProperty(name = "epa.vau.read.timeout.ms", defaultValue = "20000")
    int vauReadTimeoutMs;

    @ConfigProperty(name = "epa.vau.call.retries.ms")
    Optional<List<Integer>> vauCallRetries;

    @ConfigProperty(name = "epa.vau.call.retry.period.ms", defaultValue = "2000")
    int vauCallRetryPeriodMs;

    @ConfigProperty(name = "epa.vau.connection.timeout.ms", defaultValue = "5000")
    int connectionTimeoutMs;
}
