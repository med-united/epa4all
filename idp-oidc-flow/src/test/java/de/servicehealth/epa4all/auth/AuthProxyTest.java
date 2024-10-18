package de.servicehealth.epa4all.auth;

import de.servicehealth.vau.VauClient;
import de.servicehealth.epa4all.common.ProxyTestProfile;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(ProxyTestProfile.class)
public class AuthProxyTest extends AbstractAuthTest {

    @Override
    protected <T> T buildApi(VauClient vauClient, Class<T> clazz, String url) throws Exception {
        return ClientFactory.createProxyClient(vauClient, clazz, url);
    }
}
