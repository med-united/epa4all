package de.servicehealth.epa4all.server.bulk;

import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.filetracker.download.FileDownload;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.xds.structure.ExtrinsicContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBElement;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.IdentifiableType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static de.servicehealth.epa4all.xds.structure.ExtrinsicHelper.buildExtrinsicContext;

@ApplicationScoped
public class BulkTransfer {

    private final FolderService folderService;
    private final Event<FileDownload> eventFileDownload;

    @Inject
    public BulkTransfer(
        FolderService folderService,
        Event<FileDownload> eventFileDownload
    ) {
        this.folderService = folderService;
        this.eventFileDownload = eventFileDownload;
    }

    public List<String> downloadInsurantFiles(
        EpaContext epaContext,
        String telematikId,
        String kvnr,
        List<JAXBElement<? extends IdentifiableType>> jaxbElements
    ) {
        Set<String> checksums = folderService.getChecksums(telematikId, kvnr);
        List<String> tasks = new ArrayList<>();
        jaxbElements.stream().map(id -> {
                ExtrinsicObjectType extrinsicObject = (ExtrinsicObjectType) id.getValue();
                ExtrinsicContext extrinsicContext = buildExtrinsicContext(extrinsicObject);
                if (checksums.contains(extrinsicContext.hash())) {
                    return null;
                }
                String taskId = UUID.randomUUID().toString();
                return new FileDownload(taskId, telematikId, kvnr, extrinsicContext.uri(), epaContext, extrinsicContext);
            })
            .filter(Objects::nonNull)
            .forEach(fileDownload -> {
                eventFileDownload.fireAsync(fileDownload);
                tasks.add(fileDownload.getTaskId());
            });
        return tasks;
    }
}