package de.servicehealth.epa4all.integration.nonbc;

import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import de.health.service.cetp.IKonnektorClient;
import de.servicehealth.epa4all.common.profile.ProxyEpaTestProfile;
import de.servicehealth.epa4all.server.cetp.KonnektorClient;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.common.TestUtils.getFixture;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

@QuarkusTest
@TestProfile(ProxyEpaTestProfile.class)
public class GetWorkflowEpaIT {

    private static final Logger log = Logger.getLogger(GetWorkflowEpaIT.class.getName());

    static JAXBContext getCardsJaxbContext;

    static {
        try {
            getCardsJaxbContext = JAXBContext.newInstance(GetCardsResponse.class);
        } catch (JAXBException e) {
            log.log(Level.SEVERE, "Could not create JAXBContext", e);
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
        String fixture = getFixture("GetAllCardsResponse.xml");
        ByteArrayInputStream is = new ByteArrayInputStream(fixture.getBytes(UTF_8));
        GetCardsResponse getCardsResponse = (GetCardsResponse) getCardsJaxbContext.createUnmarshaller().unmarshal(is);
        
        KonnektorClient konnektorClientMock = mock(KonnektorClient.class);
        org.mockito.Mockito.when(konnektorClientMock.getCardsResponse(any(), any())).thenReturn(getCardsResponse);
        QuarkusMock.installMockForType(konnektorClientMock, IKonnektorClient.class);

        Response response = when().get("/workflow/cards");
        assertEquals(200, response.getStatusCode());
        String xml = response.asString();
        assertTrue(xml.contains("GetCardsResponse"));
        log.info("SOAP --> " + xml);
    }

    @Test
    public void konnektorConfigsAreExposed() {
        Response response = given().header(ACCEPT, APPLICATION_XML).when().get("/workflow/configs");
        assertEquals(200, response.getStatusCode());
        String xml = response.asString();
        assertTrue(xml.contains("KonnektorConfig"));
        assertTrue(xml.contains("clientCertificate"));

        response = given().header(ACCEPT, APPLICATION_JSON).when().get("/workflow/configs");
        assertEquals(200, response.getStatusCode());
        String json = response.asString();
        assertTrue(json.startsWith("[{"));
        assertTrue(json.contains("clientCertificate"));

        response = given()
            .header(ACCEPT, APPLICATION_JSON)
            .queryParams(Map.of(X_KONNEKTOR, "10.0.0.1"))
            .when()
            .get("/workflow/configs");
        
        assertEquals(200, response.getStatusCode());
        json = response.asString();
        assertEquals("[]", json);
    }
}
