package de.servicehealth.epa4all.server.filetracker.download;

import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FileDownloaded {

    private String telematikId;
    private RetrieveDocumentSetResponseType response;
}
