package de.servicehealth.epa4all;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import de.servicehealth.epa4all.common.ProxyTestProfile;
import de.servicehealth.epa4all.medication.fhir.restful.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.VauRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.factory.VauRestfulClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.client.fluent.Executor;
import org.hl7.fhir.r4.model.Medication;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static de.servicehealth.epa4all.common.Utils.isDockerServiceRunning;
import static de.servicehealth.utils.URLUtils.getBaseUrl;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(ProxyTestProfile.class)
public class MedicationServiceVauIT extends AbstractMedicationServiceIT {

    private static final Logger log = LoggerFactory.getLogger(MedicationServiceVauIT.class);

    @Test
    public void medicationCreatedAndObtainedThroughVAUProxy() throws Exception {
        if (isDockerServiceRunning(MEDICATION_SERVICE)) {
            FhirContext ctx = FhirContext.forR4();
            VauRestfulClientFactory.applyToFhirContext(ctx, vauClient, getBaseUrl(medicationServiceApiUrl));

            IMedicationClient medicationClient = ctx.newRestfulClient(IMedicationClient.class, medicationServiceApiUrl);
            IGenericClient genericClient = ctx.newRestfulGenericClient(medicationServiceApiUrl);
            MethodOutcome methodOutcome = genericClient.create().resource(prepareMedication()).execute();
            Long id = methodOutcome.getId().getIdPartAsLong();
            assertNotNull(id);

            // TODO: assertThrows -> FhirClientConnectionException
            // DataFormatException: HAPI-1814: Incorrect resource type found, expected "Medication" but found "Bundle"
            assertThrows(
                FhirClientConnectionException.class,
                () -> genericClient.read().resource(Medication.class).withId(id).execute()
            );
            assertThrows(
                FhirClientConnectionException.class,
                () -> medicationClient.get(methodOutcome.getId())
            );
        }
    }

    @Test
    public void documentsDownloadedThroughVAUProxy() throws Exception {
        if (isDockerServiceRunning(MEDICATION_SERVICE)) {
            FhirContext ctx = FhirContext.forR4();
            Executor executor = VauRestfulClientFactory.applyToFhirContext(ctx, vauClient, getBaseUrl(medicationServiceRenderUrl));
            IRenderClient renderClient = new VauRenderClient(executor, medicationServiceRenderUrl);
            
            File file = renderClient.getPdfFile("Z123456789", "CLIENTID1234567890AB/2.1.12-45");
            assertTrue(file.exists());

            byte[] xhtmlDocument = renderClient.getXhtmlDocument("Z123456789", "CLIENTID1234567890AB/2.1.12-45", null);
            assertTrue(new String(xhtmlDocument).contains("Verordnungsdatum"));
        }
    }
}
