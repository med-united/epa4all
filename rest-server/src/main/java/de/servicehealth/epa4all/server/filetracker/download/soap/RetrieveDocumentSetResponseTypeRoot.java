package de.servicehealth.epa4all.server.filetracker.download.soap;

import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;

@Data
@XmlRootElement(name = "root")
@XmlAccessorType(XmlAccessType.FIELD)
public class RetrieveDocumentSetResponseTypeRoot {

    @XmlElement(name = "RetrieveDocumentSetResponse", namespace = "urn:ihe:iti:xds-b:2007")
    RetrieveDocumentSetResponseType response;
}
