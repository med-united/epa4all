package de.servicehealth.epa4all.server.filetracker.upload;

import de.servicehealth.epa4all.server.filetracker.FileAction;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.xds.structure.ExtrinsicContext;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString(exclude = {"documentBytes"})
public class FileRawUpload implements FileAction {

    private EpaContext epaContext;
    private String ig;
    private String taskId;
    private String contentType;
    private String languageCode;
    private String telematikId;
    private String kvnr;
    private String fileName;
    private ProvideAndRegisterDocumentSetRequestType request;
    private ExtrinsicContext extrinsicContext;
    private byte[] documentBytes;
}
