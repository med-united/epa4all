package de.servicehealth.epa4all.server.rest;

import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.cdi.FromHttpPath;
import de.servicehealth.epa4all.server.cdi.SMCBHandle;
import de.servicehealth.epa4all.server.cdi.TelematikId;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.xdsdocument.XDSDocumentService;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

import static de.servicehealth.epa4all.cxf.client.ClientFactory.USER_AGENT;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;

public abstract class AbstractResource {

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    XDSDocumentService xdsDocumentService;

    @Inject
    IdpClient idpClient;

    @Inject
    @FromHttpPath
    UserRuntimeConfig userRuntimeConfig;

    @Inject
    @TelematikId
    String telematikId;

    @Inject
    @SMCBHandle
    String smcbHandle;

    protected Map<String, Object> prepareRuntimeAttributes(InsuranceData insuranceData) throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        String insurantId = insuranceData.getInsurantId();
        attributes.put(X_INSURANT_ID, insurantId);
        attributes.put(X_USER_AGENT, USER_AGENT);
        attributes.put(VAU_NP, idpClient.getVauNpSync(userRuntimeConfig, insurantId, smcbHandle)); // TODO check if it is needed
        return attributes;
    }
}
