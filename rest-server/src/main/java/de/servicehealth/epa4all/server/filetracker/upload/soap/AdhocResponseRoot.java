package de.servicehealth.epa4all.server.filetracker.upload.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;

@Data
@XmlRootElement(name = "root")
@XmlAccessorType(XmlAccessType.FIELD)
public class AdhocResponseRoot {
    @XmlElement(name = "AdhocQueryResponse", namespace = "urn:oasis:names:tc:ebxml-regrep:xsd:query:3.0")
    private AdhocQueryResponse response;
}