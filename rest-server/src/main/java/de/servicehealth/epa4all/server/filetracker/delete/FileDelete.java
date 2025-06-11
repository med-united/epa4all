package de.servicehealth.epa4all.server.filetracker.delete;

import de.servicehealth.epa4all.server.filetracker.FileAction;
import de.servicehealth.epa4all.server.rest.EpaContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import oasis.names.tc.ebxml_regrep.xsd.lcm._3.RemoveObjectsRequest;

@Getter
@AllArgsConstructor
@ToString
public class FileDelete implements FileAction {

    private EpaContext epaContext;
    private String taskId;
    private String fileName;
    private String telematikId;
    private String kvnr;
    private RemoveObjectsRequest request;
}