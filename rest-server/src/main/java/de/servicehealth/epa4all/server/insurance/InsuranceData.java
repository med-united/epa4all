package de.servicehealth.epa4all.server.insurance;

import de.gematik.ws.fa.vsdm.vsd.v5.UCAllgemeineVersicherungsdatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCGeschuetzteVersichertendatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
@ToString(of = {"telematikId", "insurantId"})
public class InsuranceData {

    private String pz;
    private String insurantId;
    private String telematikId;
    private UCPersoenlicheVersichertendatenXML persoenlicheVersichertendaten;
    private UCGeschuetzteVersichertendatenXML geschuetzteVersichertendaten;
    private UCAllgemeineVersicherungsdatenXML allgemeineVersicherungsdaten;
}
