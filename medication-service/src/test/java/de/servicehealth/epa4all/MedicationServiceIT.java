package de.servicehealth.epa4all;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import de.gematik.vau.lib.VauClientStateMachine;
import de.servicehealth.epa4all.common.DevTestProfile;
import de.servicehealth.epa4all.medication.fhir.interceptor.FHIRRequestVAUInterceptor;
import de.servicehealth.epa4all.medication.fhir.interceptor.FHIRResponseVAUInterceptor;
import de.servicehealth.epa4all.medication.fhir.restful.VauRestfulClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static de.servicehealth.epa4all.TransportUtils.createFakeSSLContext;
import static de.servicehealth.epa4all.common.Utils.isDockerServiceRunning;
import static org.apache.http.client.fluent.Executor.newInstance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(DevTestProfile.class)
public class MedicationServiceIT {

    private static final Logger log = LoggerFactory.getLogger(MedicationServiceIT.class);
    private static final String MEDICATION_SERVICE = "medication-service";
    private static final String PDF_EXT = ".pdf";

    @Inject
    @ConfigProperty(name = "medication-service.base.url")
    String medicationServiceBaseUrl;

    @Inject
    @ConfigProperty(name = "medication-service.api.url")
    String medicationServiceApiUrl;

    @Inject
    @ConfigProperty(name = "medication-service.render.url")
    String medicationServiceRenderUrl;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeEach
    public void before() {
        Stream.of(Objects.requireNonNull(
            new File(".").listFiles((dir, name) -> name.endsWith(PDF_EXT)))
        ).forEach(File::delete);
    }

    @Test
    public void medicationCreatedOnMedicationServiceThroughVAUProxy() throws Exception {
        if (isDockerServiceRunning(MEDICATION_SERVICE)) {

            SSLContext sslContext = createFakeSSLContext();
            URI medicationBaseUri = URI.create(medicationServiceBaseUrl);
            VauClient vauClient = new VauClient(new VauClientStateMachine());
            FHIRRequestVAUInterceptor requestInterceptor = new FHIRRequestVAUInterceptor(medicationBaseUri, sslContext, vauClient);
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

            // TODO newRestfulClient
            IGenericClient client = ctx.newRestfulGenericClient(medicationServiceApiUrl);
            MethodOutcome methodOutcome = client.create().resource(prepareMedication()).execute();
            Long id = methodOutcome.getId().getIdPartAsLong();
            assertNotNull(id);

            // Medication medication = client.read().resource(Medication.class).withId(id).execute();
            // assertEquals(id, medication.getIdElement().getIdPartAsLong());

            URI medicationRenderUri = URI.create(medicationServiceRenderUrl + "/pdf");
            Executor executor = newInstance(vauHttpClient);
            Header[] headers = prepareHeaders("Z123456789", "CLIENTID1234567890AB/2.1.12-45");
            Request pdfRequest = Request.Post(medicationRenderUri).setHeaders(headers);
            Response response = executor.execute(pdfRequest);
            InputStream content = response.returnResponse().getEntity().getContent();
            File tempFile = File.createTempFile(UUID.randomUUID().toString(), PDF_EXT, new File("."));
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                outputStream.write(content.readAllBytes());
            }
            File[] pdfs = new File(".").listFiles((dir, name) -> name.endsWith(PDF_EXT));
            assertNotNull(pdfs);
            assertEquals(1, pdfs.length);
        }
    }

    private Header[] prepareHeaders(String xInsurantid, String xUseragent) {
        Header[] headers = new Header[6];
        headers[0] = new BasicHeader(HttpHeaders.CONNECTION, "Upgrade, HTTP2-Settings");
        headers[1] = new BasicHeader(HttpHeaders.ACCEPT, "*/*");
        headers[2] = new BasicHeader(HttpHeaders.UPGRADE, "h2c");
        headers[3] = new BasicHeader(HttpHeaders.USER_AGENT, "Apache-CfxClient/4.0.5");
        headers[4] = new BasicHeader("x-insurantid", xInsurantid);
        headers[5] = new BasicHeader("x-useragent", xUseragent);
        return headers;
    }

    private Medication prepareMedication() {
        Medication medication = new Medication();
        medication.setIdentifier(List.of(new Identifier().setValue("Z123456789").setSystem("http://fhir.de/sid/gkv/kvid-10")));
        medication.setManufacturer(new Reference().setIdentifier(new Identifier().setValue("R111").setSystem("http://fhir.de/sid/gkv/kvid-10")));
        medication.setAmount(new Ratio().setNumerator(new Quantity(100)));
        medication.setCode(new CodeableConcept(new Coding().setCode("CODE")));
        medication.setText(new Narrative().setStatus(Narrative.NarrativeStatus.ADDITIONAL));
        medication.setStatus(Medication.MedicationStatus.INACTIVE);
        return medication;
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
