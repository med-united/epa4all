package de.servicehealth.epa4all.auth;

import de.servicehealth.epa4all.common.ProxyTestProfile;
import de.servicehealth.vau.VauFacade;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(ProxyTestProfile.class)
public class AuthProxyTest extends AbstractAuthTest {

    @Override
    protected <T> T buildApi(VauFacade vauFacade, Class<T> clazz, String url) throws Exception {
        return clientFactory.createProxyClient(vauFacade, clazz, url);
    }
}
