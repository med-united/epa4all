package de.servicehealth.vau;

import de.gematik.vau.lib.VauClientStateMachine;
import lombok.Getter;
import lombok.Setter;

@Getter
public class VauClient {

    private final VauClientStateMachine vauStateMachine;

    @Setter
    private String vauCid;

    public VauClient(VauClientStateMachine vauStateMachine) {
        this.vauStateMachine = vauStateMachine;
    }
}