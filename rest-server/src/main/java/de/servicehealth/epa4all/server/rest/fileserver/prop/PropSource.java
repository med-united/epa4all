package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;

import java.io.File;
import java.util.Date;
import java.util.Set;

public class PropSource {
    File file;
    Set<String> smcbFolders;
    UCPersoenlicheVersichertendatenXML.Versicherter.Person person;
    int checksumsCount;
    Date expiry;
    String smcb;

    public PropSource(
        File file,
        Set<String> smcbFolders,
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person,
        int checksumsCount,
        Date expiry,
        String smcb
    ) {
        this.file = file;
        this.smcbFolders = smcbFolders;
        this.person = person;
        this.checksumsCount = checksumsCount;
        this.expiry = expiry;
        this.smcb = smcb;
    }
}
