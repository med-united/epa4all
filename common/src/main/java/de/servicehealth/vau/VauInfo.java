package de.servicehealth.vau;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VauInfo {

    private String vauCid;
    private String K2_c2s_app_data;
    private String K2_s2c_app_data;

    public String getVauNonPUTracing() {
        return K2_c2s_app_data + " " + K2_s2c_app_data;
    }
}
