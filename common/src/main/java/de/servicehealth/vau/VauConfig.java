package de.servicehealth.vau;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

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

    @ConfigProperty(name = "epa.vau.call.retries.ms", defaultValue = "3000,1000")
    List<Integer> vauCallRetries;

    @ConfigProperty(name = "epa.vau.connection.timeout.ms", defaultValue = "5000")
    int connectionTimeoutMs;

    public int getVauCallRetryPeriodMs() {
        return vauReadTimeoutMs - 2000;
    }
}
