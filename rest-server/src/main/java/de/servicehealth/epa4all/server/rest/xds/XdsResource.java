package de.servicehealth.epa4all.server.rest.xds;

import de.health.service.cetp.retry.Retrier;
import de.servicehealth.epa4all.server.bulk.BulkTransfer;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.filetracker.upload.FileUpload;
import de.servicehealth.epa4all.server.rest.AbstractResource;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.server.xdsdocument.XDSDocumentService;
import de.servicehealth.vau.VauConfig;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;

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

    @Inject
    VauConfig vauConfig;

    protected EpaContext getEpaContext(String kvnr) throws Exception {
        return Retrier.callAndRetryEx(
            vauConfig.getVauCallRetries(),
            vauConfig.getVauCallRetryPeriodMs(),
            true,
            () -> prepareEpaContext(kvnr),
            () -> false,
            EpaContext::isEntitlementValid
        );
    }

    protected AdhocQueryResponse getAdhocQueryResponse(String kvnr, EpaContext epaContext) throws Exception {
        IDocumentManagementPortType documentManagementPortType = epaFileDownloader.getDocumentManagementPortType(kvnr, epaContext);
        AdhocQueryRequest request = xdsDocumentService.get().prepareAdhocQueryRequest(kvnr);
        return epaCallGuard.callAndRetry(
            epaContext.getBackend(),
            () -> documentManagementPortType.documentRegistryRegistryStoredQuery(request)
        );
    }
}