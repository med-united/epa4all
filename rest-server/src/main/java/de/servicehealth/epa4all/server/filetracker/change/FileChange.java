package de.servicehealth.epa4all.server.filetracker.change;

import de.servicehealth.epa4all.server.filetracker.FileAction;
import de.servicehealth.epa4all.server.rest.EpaContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import oasis.names.tc.ebxml_regrep.xsd.lcm._3.SubmitObjectsRequest;

@Getter
@AllArgsConstructor
@ToString
public class FileChange implements FileAction {

    private EpaContext epaContext;
    private String taskId;
    private String fileName;
    private String telematikId;
    private String kvnr;
    private SubmitObjectsRequest request;
}