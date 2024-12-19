package de.servicehealth.epa4all.integration.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import de.servicehealth.epa4all.common.ProxyTestProfile;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.IRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.VauRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.factory.VauRestfulClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.client.fluent.Executor;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

import static de.servicehealth.epa4all.common.Utils.isDockerContainerRunning;
import static de.servicehealth.utils.ServerUtils.getBaseUrl;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(ProxyTestProfile.class)
public class MedicationServiceVauIT extends AbstractMedicationServiceIT {

    private static final Logger log = LoggerFactory.getLogger(MedicationServiceVauIT.class);

    @Test
    public void medicationCreatedAndObtainedThroughVAUProxy() throws Exception {
        if (isDockerContainerRunning(MEDICATION_SERVICE)) {
            FhirContext ctx = FhirContext.forR4();
            VauRestfulClientFactory apiClientFactory = new VauRestfulClientFactory(ctx);
            apiClientFactory.init(vauFacade, epaUserAgent, getBaseUrl(medicationServiceApiUrl));

            String kvnr = "X110485291";

            IMedicationClient medicationClient = apiClientFactory
                .newGenericClient(medicationServiceApiUrl)
                .withXHeaders(Map.of(X_BACKEND, "medication-service:8080"));
            
            MethodOutcome outcome = medicationClient.createResource(preparePatient(kvnr));
            Long id = outcome.getId().getIdPartAsLong();
            assertNotNull(id);

            List<Patient> patients = medicationClient.searchPatients(kvnr);
            assertFalse(patients.isEmpty());
            patients.forEach(p -> System.out.println(p.getIdElement()));

            Patient patient = patients.getLast();
            for (Identifier identifier : patient.getIdentifier()) {
                System.out.println("Found Insurance ID: " + identifier.getValue());
            }

            String medIdentifier = "123";
            outcome = medicationClient.createResource(prepareMedication(medIdentifier));

            MedicationRequest medicationRequest = new MedicationRequest();
            medicationRequest.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
            medicationRequest.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
            medicationRequest.setSubject(new Reference("Patient/" + patient.getIdPart()));
            medicationRequest.setMedication(new Reference("Medication/" + outcome.getId().getIdPart()));

            MethodOutcome methodOutcome = medicationClient.createResource(medicationRequest);
            System.out.println(methodOutcome);

            List<Medication> medications = medicationClient.searchMedications();
            assertFalse(medications.isEmpty());
        }
    }

    private Patient preparePatient(String kvnr) {
        Patient patient = new Patient();
        patient.setActive(true);
        patient.setIdentifier(List.of(new Identifier().setSystem("http://fhir.de/sid/gkv/kvid-10").setValue(kvnr)));
        patient.setName(List.of(new HumanName().setFamily("Chalmers").setGiven(List.of(new StringType("Peter")))));
        patient.setTelecom(List.of(new ContactPoint().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue("(03) 5555 6473").setUse(ContactPoint.ContactPointUse.WORK)));
        return patient;
    }

    @Test
    public void documentsDownloadedThroughVAUProxy() throws Exception {
        if (isDockerContainerRunning(MEDICATION_SERVICE)) {
            FhirContext ctx = FhirContext.forR4();
            VauRestfulClientFactory apiClientFactory = new VauRestfulClientFactory(ctx);
            apiClientFactory.init(vauFacade, epaUserAgent, getBaseUrl(medicationServiceRenderUrl));

            Executor executor = Executor.newInstance(apiClientFactory.getVauHttpClient());
            IRenderClient renderClient = new VauRenderClient(executor, epaUserAgent, medicationServiceRenderUrl);

            Map<String, String> xHeaders = Map.of(X_INSURANT_ID, "Z123456789", X_USER_AGENT, "CLIENTID1234567890AB/2.1.12-45");

            File file = renderClient.getPdfFile(xHeaders);
            assertTrue(file.exists());

            byte[] xhtmlDocument = renderClient.getXhtmlDocument(xHeaders);
            assertTrue(new String(xhtmlDocument).contains("Verordnungsdatum"));
        }
    }
}
