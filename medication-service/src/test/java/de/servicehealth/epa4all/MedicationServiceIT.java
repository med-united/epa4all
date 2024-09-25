package de.servicehealth.epa4all;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import de.gematik.vau.lib.VauClientStateMachine;
import de.servicehealth.epa4all.common.DevTestProfile;
import de.servicehealth.epa4all.restful.VauRestfulClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.util.List;

import static de.servicehealth.epa4all.TransportUtils.createFakeSSLContext;
import static de.servicehealth.epa4all.common.Utils.isDockerServiceRunning;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestProfile(DevTestProfile.class)
public class MedicationServiceIT {

    private static final Logger log = LoggerFactory.getLogger(MedicationServiceIT.class);
    private static final String MEDICATION_SERVICE = "medication-service";

    @Inject
    @ConfigProperty(name = "medication-service.url")
    String medicationServiceUrl;

    @Test
    public void patientCreatedOnMedicationServiceThroughVAUProxy() throws Exception {
        if (isDockerServiceRunning(MEDICATION_SERVICE)) {

            SSLContext sslContext = createFakeSSLContext();
            URI medicationUri = URI.create(medicationServiceUrl);
            VauClient vauClient = new VauClient(new VauClientStateMachine());
            FHIRRequestVAUInterceptor requestInterceptor = new FHIRRequestVAUInterceptor(medicationUri, sslContext, vauClient);
            FHIRResponseVAUInterceptor responseInterceptor = new FHIRResponseVAUInterceptor(vauClient);
            CloseableHttpClient vauHttpClient = HttpClients.custom()
                .addInterceptorFirst(requestInterceptor) // comment for plain http://localhost:8084/fhir request
                .addInterceptorLast(responseInterceptor) // comment for plain http://localhost:8084/fhir request
                .setSSLContext(sslContext)               // comment for plain http://localhost:8084/fhir request
                .build();

            FhirContext ctx = FhirContext.forR4();
            ctx.setRestfulClientFactory(new VauRestfulClientFactory(ctx)); // comment for plain http://localhost:8084/fhir request

            // todo move directly to the VauRestfulClientFactory
            IRestfulClientFactory clientFactory = ctx.getRestfulClientFactory();
            clientFactory.setHttpClient(vauHttpClient);
            clientFactory.setServerValidationMode(ServerValidationModeEnum.NEVER);

            IGenericClient client = ctx.newRestfulGenericClient(medicationServiceUrl);
            MethodOutcome methodOutcome = client.create().resource(preparePatient()).execute();
            Long id = methodOutcome.getId().getIdPartAsLong();
            assertThrows(FhirClientConnectionException.class, () -> client.read().resource(Patient.class).withId(id).execute());
            // assertEquals(id, foundPatient.getIdElement().getIdPartAsLong());
        }
    }

    private Patient preparePatient() {
        Patient patient = new Patient();
        patient.setActive(true);
        patient.setIdentifier(List.of(new Identifier().setSystem("http://fhir.de/sid/gkv/kvid-10").setValue("Z1234567891")));
        patient.setName(List.of(new HumanName().setFamily("Chalmers").setGiven(List.of(new StringType("Peter")))));
        patient.setTelecom(List.of(new ContactPoint().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue("(03) 5555 6473").setUse(ContactPoint.ContactPointUse.WORK)));
        return patient;
    }
}
