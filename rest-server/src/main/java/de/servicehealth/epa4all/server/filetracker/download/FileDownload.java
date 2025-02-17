package de.servicehealth.epa4all.server.filetracker.download;

import de.servicehealth.epa4all.server.filetracker.FileAction;
import de.servicehealth.epa4all.server.rest.EpaContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class FileDownload implements FileAction {

    private EpaContext epaContext;
    private String taskId;
    private String fileName;
    private String telematikId;
    private String kvnr;
    private String repositoryUniqueId;
}
