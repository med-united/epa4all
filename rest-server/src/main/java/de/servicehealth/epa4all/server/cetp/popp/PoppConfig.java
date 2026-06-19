package de.servicehealth.epa4all.server.cetp.popp;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class PoppConfig {

    @ConfigProperty(name = "cetp.flow", defaultValue = "cardlink")
    String cetpFlow;

    @ConfigProperty(name = "popp.client.url")
    String poppClientUrl;

    @ConfigProperty(name = "popp.client.timeout.sec")
    int poppClientTimeoutSec;

    @ConfigProperty(name = "popp.vsdm.url")
    String poppVsdmUrl;

    @ConfigProperty(name = "popp.vsdm.timeout.sec")
    int poppVsdmTimeoutSec;
}
