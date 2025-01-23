package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;

import java.io.File;

public class PropSource {
    File file;
    UCPersoenlicheVersichertendatenXML.Versicherter.Person person;

    public PropSource(File file, UCPersoenlicheVersichertendatenXML.Versicherter.Person person) {
        this.file = file;
        this.person = person;
    }
}
