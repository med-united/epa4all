package de.servicehealth.epa4all.integration.info;

import de.servicehealth.epa4all.common.profile.ProxyLocalTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(ProxyLocalTestProfile.class)
public class InformationServiceProxyIT extends AbstractInformationServiceIT {
}
