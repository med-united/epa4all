package de.servicehealth.vau;

import de.gematik.vau.lib.VauClientStateMachine;
import lombok.Getter;
import lombok.Setter;

@Getter
public class VauClient {
    
    public static final String VAU_CID = "VAU-CID";

    public static final String VAU_DEBUG_SK1_C2S = "vau-debug-s_k1_c2s";
    public static final String VAU_DEBUG_SK1_S2C = "vau-debug-s_k1_s2c";
    public static final String VAU_DEBUG_SK2_C2S_INFO = "vau-debug-s_k2_c2s_keyconfirmation";
    public static final String VAU_DEBUG_SK2_S2C_INFO = "vau-debug-s_k2_s2c_keyconfirmation";

    public static final String VAU_NON_PU_TRACING = "VAU-nonPU-Tracing";

    private final VauClientStateMachine vauStateMachine;

    @Setter
    private VauInfo vauInfo;
    
    @Setter
    private String np;
    
    @Setter
    private String xInsurantId;

    public VauClient(VauClientStateMachine vauStateMachine) {
        this.vauStateMachine = vauStateMachine;
    }
}