package de.servicehealth.epa4all.integration.bc.wiremock;

import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import de.health.service.cetp.IKonnektorClient;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.integration.bc.wiremock.setup.CallInfo;
import de.servicehealth.epa4all.server.cetp.KonnektorClient;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.List;

import static de.servicehealth.epa4all.common.TestUtils.getTextFixture;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class EventServiceEpaIT extends AbstractWiremockTest {

    private static final Logger log = LoggerFactory.getLogger(EventServiceEpaIT.class.getName());

    static JAXBContext getCardsJaxbContext;

    static {
        try {
            getCardsJaxbContext = JAXBContext.newInstance(GetCardsResponse.class);
        } catch (JAXBException e) {
            log.error("Could not create JAXBContext", e);
        }
    }

    @Inject
    IKonnektorClient konnektorClient;

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(konnektorClient, IKonnektorClient.class);
    }

    @Test
    public void getCardsResponseIsExposed() throws Exception {
        String validToValue = "2025-02-15T22:59:59";
        String validToPayload = "{\"validTo\":\"" + validToValue + "\"}";
        byte[] payload = validToPayload.getBytes(UTF_8);
        CallInfo callInfo = new CallInfo().withJsonPayload(payload);
        List<Pair<String, CallInfo>> responseFuncs = List.of(
            Pair.of("/epa/basic/api/v1/ps/entitlements", callInfo)
        );
        initStubs(204, responseFuncs, MEDICATION_PERMIT_MAP);

        byte[] fixture = getTextFixture("GetAllCardsResponse.xml");
        ByteArrayInputStream is = new ByteArrayInputStream(fixture);
        GetCardsResponse getCardsResponse = (GetCardsResponse) getCardsJaxbContext.createUnmarshaller().unmarshal(is);
        
        KonnektorClient konnektorClientMock = mock(KonnektorClient.class);
        when(konnektorClientMock.getCardsResponse(any(), any())).thenReturn(getCardsResponse);
        QuarkusMock.installMockForType(konnektorClientMock, IKonnektorClient.class);

        Response response = given().when().get("/event/cards");
        assertEquals(200, response.getStatusCode());
        String xml = response.asString();
        assertTrue(xml.contains("GetCardsResponse"));
        log.info("SOAP --> " + xml);
    }
}
