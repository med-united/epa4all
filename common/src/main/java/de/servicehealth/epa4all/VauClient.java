package de.servicehealth.epa4all;

import de.gematik.vau.lib.VauClientStateMachine;

public class VauClient {

    private final VauClientStateMachine vauStateMachine;

    private String vauCid;

    public VauClient(VauClientStateMachine vauStateMachine) {
        this.vauStateMachine = vauStateMachine;
    }

    public VauClientStateMachine getVauStateMachine() {
        return vauStateMachine;
    }

    public String getVauCid() {
        return vauCid;
    }

    public void setVauCid(String vauCid) {
        this.vauCid = vauCid;
    }
}