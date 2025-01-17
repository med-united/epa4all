package de.servicehealth.epa4all.server.idp.vaunp;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class VauNpKey {

    private String smcbHandle;
    private String konnektor;
    private String epaBackend;
}
