package de.servicehealth.epa4all.server.vsd;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class VsdConfig {

    @ConfigProperty(name = "vsd.handles.test.mode", defaultValue = "true")
    boolean handlesTestMode;

    @ConfigProperty(name = "vsd.primary.iccsn")
    String primaryIccsn;

    @ConfigProperty(name = "vsd.test.smcb.cardholder.name")
    String testSmcbCardholderName;
}
