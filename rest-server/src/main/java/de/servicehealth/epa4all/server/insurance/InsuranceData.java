package de.servicehealth.epa4all.server.insurance;

import de.gematik.ws.fa.vsdm.vsd.v5.UCAllgemeineVersicherungsdatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCGeschuetzteVersichertendatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InsuranceData {

    private String pz;
    private String xInsurantId;
    private UCPersoenlicheVersichertendatenXML persoenlicheVersichertendaten;
    private UCGeschuetzteVersichertendatenXML geschuetzteVersichertendaten;
    private UCAllgemeineVersicherungsdatenXML allgemeineVersicherungsdaten;
}
