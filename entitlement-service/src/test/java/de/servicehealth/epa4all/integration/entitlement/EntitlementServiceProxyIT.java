package de.servicehealth.epa4all.integration.entitlement;

import de.servicehealth.epa4all.common.ProxyTestProfile;
import de.servicehealth.vau.VauFacade;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(ProxyTestProfile.class)
public class EntitlementServiceProxyIT extends AbstractEntitlementServiceIT {

    @Override
    protected <T> T buildApi(VauFacade vauFacade, Class<T> clazz, String url) throws Exception {
        return clientFactory.createProxyClient(vauFacade, epaUserAgent, clazz, url);
    }
}
