package de.servicehealth.epa4all;

import de.servicehealth.epa4all.common.PlainTestProfile;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.vau.VauClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(PlainTestProfile.class)
public class EntitlementServicePlainIT extends AbstractEntitlementServiceIT {

    @Override
    protected <T> T buildApi(VauClient vauClient, Class<T> clazz, String url) throws Exception {
        return ClientFactory.createPlainClient(clazz, url);
    }
}