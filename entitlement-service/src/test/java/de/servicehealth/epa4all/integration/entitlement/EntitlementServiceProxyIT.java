package de.servicehealth.epa4all.integration.entitlement;

import de.servicehealth.epa4all.common.profile.ProxyLocalTestProfile;
import de.servicehealth.vau.VauFacade;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

import java.util.List;
import java.util.Set;

@QuarkusTest
@TestProfile(ProxyLocalTestProfile.class)
public class EntitlementServiceProxyIT extends AbstractEntitlementServiceIT {

    @Override
    protected <T> T buildApi(VauFacade vauFacade, Class<T> clazz, String url) throws Exception {
        return clientFactory.createRestProxyClient(
            vauFacade, epaUserAgent, clazz, url, Set.of(), Set.of(), List.of()
        );
    }
}
