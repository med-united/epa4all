package de.servicehealth.epa4all.server.filetracker.download;

import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString(of = {"telematikId", "kvnr"})
public class FileDownloaded {

    private String kvnr;
    private String telematikId;
    private RetrieveDocumentSetResponseType response;
}
