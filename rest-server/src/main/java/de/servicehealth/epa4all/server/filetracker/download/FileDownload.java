package de.servicehealth.epa4all.server.filetracker.download;

import de.servicehealth.epa4all.server.filetracker.FileAction;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.xds.structure.ExtrinsicContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class FileDownload implements FileAction {

    private String taskId;
    private String telematikId;
    private String kvnr;
    private String fileName;
    private EpaContext epaContext;
    private ExtrinsicContext extrinsicContext;
}
