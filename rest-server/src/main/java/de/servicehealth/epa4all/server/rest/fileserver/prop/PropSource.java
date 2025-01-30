package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;

import java.io.File;
import java.util.Date;
import java.util.Set;

public class PropSource {
    File file;
    UCPersoenlicheVersichertendatenXML.Versicherter.Person person;
    Set<String> smcbFolders;
    int checksumsCount;
    Date expiry;
    String smcb;

    public PropSource(
        File file,
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person,
        Set<String> smcbFolders,
        int checksumsCount,
        Date expiry,
        String smcb
    ) {
        this.file = file;
        this.person = person;
        this.smcbFolders = smcbFolders;
        this.checksumsCount = checksumsCount;
        this.expiry = expiry;
        this.smcb = smcb;
    }
}
