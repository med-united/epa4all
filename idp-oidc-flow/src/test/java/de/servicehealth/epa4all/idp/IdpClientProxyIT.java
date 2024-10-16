package de.servicehealth.epa4all.idp;

import de.servicehealth.epa4all.common.ProxyTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(ProxyTestProfile.class)
public class IdpClientProxyIT extends IdpClientIT {
}

