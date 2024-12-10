package de.servicehealth.epa4all.integration.info;

import de.servicehealth.epa4all.common.ProxyTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(ProxyTestProfile.class)
public class InformationServiceProxyIT extends AbstractInformationServiceIT {
}
