package de.servicehealth.epa4all.server.idp.vaunp;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class VauNpKey {

    private String konnektor;
    private String epaBackend;
}
