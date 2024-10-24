package de.servicehealth.epa4all.cxf;

import de.gematik.vau.lib.VauClientStateMachine;
import de.servicehealth.vau.VauClient;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VauClientFactory {

    public VauClient getVauClient() {
        return new VauClient(new VauClientStateMachine());
    }
}
