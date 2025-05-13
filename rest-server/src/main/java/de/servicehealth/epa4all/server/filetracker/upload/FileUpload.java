package de.servicehealth.epa4all.server.filetracker.upload;

import de.servicehealth.epa4all.server.filetracker.FileAction;
import de.servicehealth.epa4all.server.rest.EpaContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString(exclude = {"documentBytes"})
public class FileUpload implements FileAction {

    private EpaContext epaContext;
    private String ig;
    private String taskId;
    private String contentType;
    private String languageCode;
    private String telematikId;
    private String kvnr;
    private String fileName;
    private String title;
    private String authorLanr;
    private String authorFirstName;
    private String authorLastName;
    private String authorTitle;
    private String praxis;
    private String practiceSetting;
    private String information;
    private String information2;
    private String folderName;
    private byte[] documentBytes;
}