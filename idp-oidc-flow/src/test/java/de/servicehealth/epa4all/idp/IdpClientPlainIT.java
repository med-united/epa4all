package de.servicehealth.epa4all.idp;

import de.servicehealth.epa4all.common.PlainTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(PlainTestProfile.class)
public class IdpClientPlainIT extends IdpClientIT {
}
