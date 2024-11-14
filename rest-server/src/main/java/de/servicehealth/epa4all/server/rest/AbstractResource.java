package de.servicehealth.epa4all.server.rest;

import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.cdi.FromHttpPath;
import de.servicehealth.epa4all.server.cdi.SMCBHandle;
import de.servicehealth.epa4all.server.cdi.TelematikId;
import de.servicehealth.epa4all.server.xdsdocument.XDSDocumentService;
import jakarta.inject.Inject;

public abstract class AbstractResource {

    @Inject
    XDSDocumentService xdsDocumentService;

    @Inject
    @FromHttpPath
    UserRuntimeConfig userRuntimeConfig;

    @Inject
    @TelematikId
    String telematikId;

    @Inject
    @SMCBHandle
    String smcbHandle;
}
