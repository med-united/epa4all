package de.servicehealth.epa4all.server.rest.xds;

import de.servicehealth.epa4all.server.bulk.BulkTransfer;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.filetracker.upload.FileUpload;
import de.servicehealth.epa4all.server.rest.AbstractResource;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.server.xdsdocument.XDSDocumentService;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;

import java.util.Map;
import java.util.UUID;

public abstract class XdsResource extends AbstractResource {

    public static final String XDS_DOCUMENT_PATH = "xds-document";

    @Inject
    Instance<XDSDocumentService> xdsDocumentService;

    @Inject
    EpaFileDownloader epaFileDownloader;

    @Inject
    Event<FileUpload> eventFileUpload;

    @Inject
    BulkTransfer bulkTransfer;

    @Inject
    EpaCallGuard epaCallGuard;


    protected AdhocQueryResponse getAdhocQueryResponse(String kvnr, EpaContext epaContext) throws Exception {
        Map<String, String> xHeaders = epaContext.getXHeaders();
        IDocumentManagementPortType documentManagementPortType = epaMultiService
            .findEpaAPI(epaContext.getInsurantId())
            .getDocumentManagementPortType(UUID.randomUUID().toString(), xHeaders);
        AdhocQueryRequest request = xdsDocumentService.get().prepareAdhocQueryRequest(kvnr);
        return epaCallGuard.callAndRetry(
            epaContext.getBackend(),
            () -> documentManagementPortType.documentRegistryRegistryStoredQuery(request)
        );
    }
}
