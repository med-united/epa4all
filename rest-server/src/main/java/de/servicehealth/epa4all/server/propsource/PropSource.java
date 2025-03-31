package de.servicehealth.epa4all.server.propsource;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.File;
import java.util.Date;
import java.util.Map;
import java.util.Set;

@Getter
@AllArgsConstructor
public class PropSource {
    
    private final File file;
    private final Map<String, String> smcbFolders;
    private final UCPersoenlicheVersichertendatenXML.Versicherter.Person person;
    private final int checksumsCount;
    private final Date expiry;
    private final String smcb;
}
