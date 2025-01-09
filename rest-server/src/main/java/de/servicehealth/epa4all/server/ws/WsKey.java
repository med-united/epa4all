package de.servicehealth.epa4all.server.ws;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString(of = {"telematikId", "kvnr"})
public class WsKey {

    private String kvnr;
    private String telematikId;
}
