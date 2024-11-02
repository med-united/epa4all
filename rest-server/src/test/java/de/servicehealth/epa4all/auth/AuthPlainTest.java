package de.servicehealth.epa4all.auth;

import de.servicehealth.epa4all.common.PlainTestProfile;
import de.servicehealth.vau.VauClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(PlainTestProfile.class)
public class AuthPlainTest extends AbstractAuthTest {

    @Override
    protected <T> T buildApi(VauClient vauClient, Class<T> clazz, String url) throws Exception {
        return clientFactory.createPlainClient(clazz, url);
    }
}
