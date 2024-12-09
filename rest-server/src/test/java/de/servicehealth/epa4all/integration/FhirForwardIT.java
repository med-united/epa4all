package de.servicehealth.epa4all.integration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import de.servicehealth.epa4all.common.ProxyTestProfile;
import de.servicehealth.epa4all.medication.fhir.restful.extension.ForwardingRestfulClientFactory;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IMedicationClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ca.uhn.fhir.rest.client.api.ServerValidationModeEnum.NEVER;
import static de.servicehealth.epa4all.common.Utils.isDockerContainerRunning;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@QuarkusTest
@TestProfile(ProxyTestProfile.class)
public class FhirForwardIT {

    public static final String MEDICATION_SERVICE = "medication-service";

    @Test
    public void patientCreationIsForwardedToEPA() throws Exception {
        if (isDockerContainerRunning(MEDICATION_SERVICE)) {
            FhirContext ctx = FhirContext.forR4();

            ForwardingRestfulClientFactory forwardingClientFactory = new ForwardingRestfulClientFactory("localhost", ctx);
            forwardingClientFactory.setSocketTimeout(120000); // 10 seconds
            forwardingClientFactory.setConnectTimeout(12000);

            forwardingClientFactory.setServerValidationMode(NEVER);
            String forwardUrl = "http://localhost:8889/fhir";
            IMedicationClient forwardingClient = forwardingClientFactory.newGenericClient(forwardUrl);

            String kvnr = "X110485291";
            
            MethodOutcome methodOutcome = forwardingClient.withKvnr(kvnr).createResource(preparePatient(kvnr));
            assertInstanceOf(Patient.class, methodOutcome.getResource());

            List<Patient> patients = forwardingClient.searchPatients(kvnr);
            assertFalse(patients.isEmpty());

            Patient patient = patients.getLast();

            String medIdentifier = "123";
            methodOutcome = forwardingClient.createResource(prepareMedication(medIdentifier));

            MedicationRequest medicationRequest = new MedicationRequest();
            medicationRequest.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
            medicationRequest.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
            medicationRequest.setSubject(new Reference("Patient/" + patient.getIdPart()));
            medicationRequest.setMedication(new Reference("Medication/" + methodOutcome.getId().getIdPart()));

            methodOutcome = forwardingClient.createResource(medicationRequest);
            assertInstanceOf(MedicationRequest.class, methodOutcome.getResource());

            List<Medication> medications = forwardingClient.searchMedications();
            assertFalse(medications.isEmpty());
        }
    }

    @Test
    public void testHealthEndpoint() {
        given()
            .when().get("/health")
            .then()
            .statusCode(200)
            .body(containsString("UP"));
    }

    private Patient preparePatient(String kvnr) {
        Patient patient = new Patient();
        patient.setActive(true);
        patient.setIdentifier(List.of(new Identifier().setSystem("http://fhir.de/sid/gkv/kvid-10").setValue(kvnr)));
        patient.setName(List.of(new HumanName().setFamily("Chalmers").setGiven(List.of(new StringType("Peter")))));
        patient.setTelecom(List.of(new ContactPoint().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue("(03) 5555 6473").setUse(ContactPoint.ContactPointUse.WORK)));
        return patient;
    }

    protected Medication prepareMedication(String medId) {
        Medication medication = new Medication();
        medication.setId(medId);
        medication.setAmount(new Ratio().setNumerator(new Quantity(100)));
        medication.setCode(new CodeableConcept().setText("Amoxicillin"));
        medication.setText(new Narrative().setStatus(Narrative.NarrativeStatus.ADDITIONAL));
        medication.setStatus(Medication.MedicationStatus.INACTIVE);
        return medication;
    }
}
