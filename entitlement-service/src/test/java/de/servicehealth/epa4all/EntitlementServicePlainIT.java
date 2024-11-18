package de.servicehealth.epa4all;

import de.servicehealth.epa4all.common.PlainTestProfile;
import de.servicehealth.vau.VauFacade;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(PlainTestProfile.class)
public class EntitlementServicePlainIT extends AbstractEntitlementServiceIT {

    @Override
    protected <T> T buildApi(VauFacade vauFacade, Class<T> clazz, String url) throws Exception {
        return clientFactory.createPlainClient(clazz, url);
    }
}