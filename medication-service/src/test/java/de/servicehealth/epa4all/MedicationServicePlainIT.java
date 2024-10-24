package de.servicehealth.epa4all;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.servicehealth.epa4all.common.PlainTestProfile;
import de.servicehealth.epa4all.medication.fhir.restful.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.PlainRenderClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.client.fluent.Executor;
import org.apache.http.impl.client.HttpClients;
import org.hl7.fhir.r4.model.Medication;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static de.servicehealth.epa4all.common.Utils.isDockerServiceRunning;
import static de.servicehealth.utils.SSLUtils.createFakeSSLContext;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(PlainTestProfile.class)
public class MedicationServicePlainIT extends AbstractMedicationServiceIT {

    private static final Logger log = LoggerFactory.getLogger(MedicationServicePlainIT.class);

    @Test
    public void medicationSemiCRUDWorks() throws Exception {
        if (isDockerServiceRunning(MEDICATION_SERVICE)) {
            FhirContext ctx = FhirContext.forR4();

            IMedicationClient medicationClient = ctx.newRestfulClient(IMedicationClient.class, medicationServiceApiUrl);
            IGenericClient genericClient = ctx.newRestfulGenericClient(medicationServiceApiUrl);
            MethodOutcome methodOutcome = genericClient.create().resource(prepareMedication()).execute();
            Long id = methodOutcome.getId().getIdPartAsLong();
            assertNotNull(id);

            Medication medication = genericClient.read().resource(Medication.class).withId(id).execute();
            assertNotNull(medication);
            medication = medicationClient.get(methodOutcome.getId());
            assertNotNull(medication);
        }
    }

    @Test
    public void documentsFetched() throws Exception {
        if (isDockerServiceRunning(MEDICATION_SERVICE)) {
            Executor executor = Executor.newInstance(HttpClients.custom().setSSLContext(createFakeSSLContext()).build());
            IRenderClient renderClient = new PlainRenderClient(executor, medicationServiceRenderUrl);

            File file = renderClient.getPdfFile("Z123456789", "CLIENTID1234567890AB/2.1.12-45");
            assertTrue(file.exists());

            String xhtmlDocument = renderClient.getXhtmlDocument("Z123456789", "CLIENTID1234567890AB/2.1.12-45");
            assertTrue(xhtmlDocument.contains("Verordnungsdatum"));
        }
    }
}
