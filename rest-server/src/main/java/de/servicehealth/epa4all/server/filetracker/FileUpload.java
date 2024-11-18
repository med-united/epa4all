package de.servicehealth.epa4all.server.filetracker;

import de.servicehealth.epa4all.server.rest.EpaContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString(exclude = {"documentBytes"})
public class FileUpload implements FileAction {

    private EpaContext epaContext;
    private String taskId;
    private String contentType;
    private String languageCode;
    private String telematikId;
    private String kvnr;
    private String fileName;
    private String folderName;
    private byte[] documentBytes;
}
