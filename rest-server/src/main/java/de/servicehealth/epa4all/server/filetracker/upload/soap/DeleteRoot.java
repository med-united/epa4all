package de.servicehealth.epa4all.server.filetracker.upload.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;
import oasis.names.tc.ebxml_regrep.xsd.lcm._3.RemoveObjectsRequest;

@Data
@XmlRootElement(name = "root")
@XmlAccessorType(XmlAccessType.FIELD)
public class DeleteRoot {
    @XmlElement(name = "RemoveObjectsRequest", namespace = "urn:oasis:names:tc:ebxml-regrep:xsd:lcm:3.0")
    private RemoveObjectsRequest request;
}