package de.servicehealth.epa4all;

import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.api.ConsentDecisionsApi;
import de.servicehealth.epa4all.common.DockerAction;
import de.servicehealth.epa4all.common.Utils;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.model.GetConsentDecisionInformation200Response;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

public abstract class AbstractInformationServiceIT {

    public static final String INFORMATION_SERVICE = "information-service";

    @Inject
    @ConfigProperty(name = "information-service.url")
    String informationServiceUrl;

    private void runWithDocker(DockerAction action) throws Exception {
        Utils.runWithDocker(INFORMATION_SERVICE, action);
    }

    @Test
    public void getRecordStatusWorks() throws Exception {
        runWithDocker(() -> {
            AccountInformationApi api = ClientFactory.createPlainClient(AccountInformationApi.class, informationServiceUrl);
            assertDoesNotThrow(() -> api.getRecordStatus("Z1234567890", "PSSIM123456789012345/1.2.4"));
        });
    }
    @Test
    public void getConsentDecisionWorks() throws Exception {
        runWithDocker(() -> {
            ConsentDecisionsApi api = ClientFactory.createPlainClient(ConsentDecisionsApi.class, informationServiceUrl);
            GetConsentDecisionInformation200Response response = api.getConsentDecisionInformation("Z1234567890", "PSSIM123456789012345/1.2.4");
            assertFalse(response.getData().isEmpty());
        });
    }
}
