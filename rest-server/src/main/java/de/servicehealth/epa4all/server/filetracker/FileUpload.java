package de.servicehealth.epa4all.server.filetracker;

import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString(exclude = {"documentBytes", "request", "structureDefinition"})
public class FileUpload {

    private String taskId;
    private String contentType;
    private String languageCode;
    private String telematikId;
    private String kvnr;
    private String fileName;
    private String folderName;
    private EpaContext epaContext;
    private byte[] documentBytes;
    private ProvideAndRegisterDocumentSetRequestType request;
    private StructureDefinition structureDefinition;
}
