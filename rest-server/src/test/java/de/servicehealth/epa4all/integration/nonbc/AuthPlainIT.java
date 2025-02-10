package de.servicehealth.epa4all.integration.nonbc;

import de.servicehealth.epa4all.common.profile.PlainLocalTestProfile;
import de.servicehealth.epa4all.integration.base.AbstractAuthIT;
import de.servicehealth.vau.VauFacade;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(PlainLocalTestProfile.class)
public class AuthPlainIT extends AbstractAuthIT {

    @Override
    protected <T> T buildApi(VauFacade vauFacade, Class<T> clazz, String url) throws Exception {
        return clientFactory.createRestPlainClient(clazz, url);
    }
}
