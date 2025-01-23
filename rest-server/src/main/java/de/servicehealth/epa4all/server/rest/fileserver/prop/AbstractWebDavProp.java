package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import jakarta.inject.Inject;

import java.net.URI;

public abstract class AbstractWebDavProp implements WebDavProp {

    @Inject
    WebdavConfig webdavConfig;

    @Inject
    InsuranceDataService insuranceDataService;
    
    protected InsuranceData getInsuranceData(URI requestUri) {
        String path = requestUri.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        String telematikId = path.split("/")[0].trim();
        String kvnr = path.split("/")[1].trim();

        return insuranceDataService.getLocalInsuranceData(telematikId, kvnr);
    }
}
