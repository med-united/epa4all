package de.servicehealth.epa4all;

import de.servicehealth.epa4all.common.ProxyTestProfile;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(ProxyTestProfile.class)
public class EntitlementServiceProxyIT extends AbstractEntitlementServiceIT {

    @Override
    protected <T> T buildApi(Class<T> clazz, String url) throws Exception {
        return ClientFactory.createProxyClient(clazz, url);
    }
}
