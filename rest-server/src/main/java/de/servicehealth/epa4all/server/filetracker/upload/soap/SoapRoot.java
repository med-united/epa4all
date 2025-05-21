package de.servicehealth.epa4all.server.filetracker.upload.soap;

import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;

@Data
@XmlRootElement(name = "root")
@XmlAccessorType(XmlAccessType.FIELD)
public class SoapRoot {
    @XmlElement(name = "ProvideAndRegisterDocumentSetRequest", namespace = "urn:ihe:iti:xds-b:2007")
    private ProvideAndRegisterDocumentSetRequestType request;
}
