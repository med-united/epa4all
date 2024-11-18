package de.servicehealth.epa4all.server.cdi;

import de.health.service.cetp.domain.fault.CetpFault;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@RequestScoped
public class TelematikIdRequestScopeProducer {

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    @FromHttpPath
    UserRuntimeConfig userRuntimeConfig;

    String smcbHandle;

    private void initSmcbHandle() throws CetpFault {
        if (smcbHandle == null) {
            smcbHandle = insuranceDataService.getSmcbHandle(userRuntimeConfig);
        }
    }

    @Produces
    @TelematikId
    public String telematikId() {
        try {
            initSmcbHandle();
            return insuranceDataService.getTelematikId(userRuntimeConfig, smcbHandle);
        } catch (CetpFault e) {
            throw new RuntimeException(e);
        }
    }

    @Produces
    @SMCBHandle
    public String smcbHandle() {
        try {
            initSmcbHandle();
            return smcbHandle;
        } catch (CetpFault e) {
            throw new RuntimeException(e);
        }
    }
}
