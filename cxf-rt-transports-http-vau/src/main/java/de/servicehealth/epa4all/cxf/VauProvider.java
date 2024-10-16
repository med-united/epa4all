package de.servicehealth.epa4all.cxf;

import de.servicehealth.epa4all.vau.VauClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import static de.servicehealth.epa4all.cxf.client.ClientFactory.initVauTransport;

@ApplicationScoped
public class VauProvider {

    @Produces
    @Dependent
    VauClient getVauClient() {
        return new VauClient(initVauTransport());
    }
}
