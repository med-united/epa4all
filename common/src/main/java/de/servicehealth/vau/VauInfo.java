package de.servicehealth.vau;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VauInfo {

    private String vauCid;
    private String vauDebugCS;
    private String vauDebugSC;

    public String getVauNonPUTracing() {
        return vauDebugCS + " " + vauDebugSC;
    }
}
