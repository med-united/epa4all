package de.servicehealth.epa4all;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import de.servicehealth.epa4all.common.DevTestProfile;
import de.servicehealth.epa4all.medication.fhir.restful.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.IRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.RawRequestRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.VauRestfulClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.http.client.fluent.Executor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static de.servicehealth.epa4all.common.Utils.isDockerServiceRunning;
import static de.servicehealth.epa4all.medication.fhir.restful.RawRequestRenderClient.PDF_EXT;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(DevTestProfile.class)
public class MedicationServiceIT {

    private static final Logger log = LoggerFactory.getLogger(MedicationServiceIT.class);

    private static final String MEDICATION_SERVICE = "medication-service";

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
    public void medicationCreatedAndObtainedThroughVAUProxy() throws Exception {
        if (isDockerServiceRunning(MEDICATION_SERVICE)) {
            FhirContext ctx = FhirContext.forR4();
            VauRestfulClientFactory.applyToFhirContext(ctx, medicationServiceBaseUrl);

            IGenericClient client = ctx.newRestfulGenericClient(medicationServiceApiUrl);
            MethodOutcome methodOutcome = client.create().resource(prepareMedication()).execute();
            Long id = methodOutcome.getId().getIdPartAsLong();
            assertNotNull(id);

            // TODO: assertThrows -> FhirClientConnectionException
            // DataFormatException: HAPI-1814: Incorrect resource type found, expected "Medication" but found "Bundle"
            
            assertThrows(
                FhirClientConnectionException.class,
                () -> client.read().resource(Medication.class).withId(id).execute()
            );

            IMedicationClient mc = ctx.newRestfulClient(IMedicationClient.class, medicationServiceApiUrl);
            assertThrows(
                FhirClientConnectionException.class,
                () -> mc.get(methodOutcome.getId())
            );
        }
    }

    @Test
    public void documentsDownloadedThroughVAUProxy() throws Exception {
        if (isDockerServiceRunning(MEDICATION_SERVICE)) {
            FhirContext ctx = FhirContext.forR4();
            Executor executor = VauRestfulClientFactory.applyToFhirContext(ctx, medicationServiceBaseUrl);
            IRenderClient renderClient = new RawRequestRenderClient(executor, medicationServiceRenderUrl);
            
            File file = renderClient.getPdfDocument("Z123456789", "CLIENTID1234567890AB/2.1.12-45");
            assertTrue(file.exists());

            String xhtmlDocument = renderClient.getXhtmlDocument("Z123456789", "CLIENTID1234567890AB/2.1.12-45");
            assertTrue(xhtmlDocument.contains("Verordnungsdatum"));
        }
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
}
