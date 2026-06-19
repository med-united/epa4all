package de.servicehealth.epa4all.server.cetp.popp;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PoppTokenRequest {

    private String communicationType;
    private String egkCardHandle;
}
