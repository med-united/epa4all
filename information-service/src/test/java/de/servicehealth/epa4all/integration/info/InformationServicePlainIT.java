package de.servicehealth.epa4all.integration.info;

import de.servicehealth.epa4all.common.profile.PlainLocalTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(PlainLocalTestProfile.class)
public class InformationServicePlainIT extends AbstractInformationServiceIT {
}
