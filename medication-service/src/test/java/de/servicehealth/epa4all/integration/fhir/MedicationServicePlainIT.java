package de.servicehealth.epa4all.integration.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.servicehealth.epa4all.common.PlainTestProfile;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.IRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.PlainRenderClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.client.fluent.Executor;
import org.apache.http.impl.client.HttpClients;
import org.hl7.fhir.r4.model.Medication;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

import static de.servicehealth.epa4all.common.Utils.isDockerContainerRunning;
import static de.servicehealth.utils.SSLUtils.createFakeSSLContext;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(PlainTestProfile.class)
public class MedicationServicePlainIT extends AbstractMedicationServiceIT {

    private static final Logger log = LoggerFactory.getLogger(MedicationServicePlainIT.class);

    @Test
    public void medicationSemiCRUDWorks() throws Exception {
        if (isDockerContainerRunning(MEDICATION_SERVICE)) {
            FhirContext ctx = FhirContext.forR4();

            IGenericClient genericClient = ctx.newRestfulGenericClient(medicationServiceApiUrl);
            MethodOutcome methodOutcome = genericClient.create().resource(prepareMedication("1")).execute();
            Long id = methodOutcome.getId().getIdPartAsLong();
            assertNotNull(id);

            Medication medication = genericClient.read().resource(Medication.class).withId(id).execute();
            assertNotNull(medication);
        }
    }

    @Test
    public void documentsFetched() throws Exception {
        if (isDockerContainerRunning(MEDICATION_SERVICE)) {
            Executor executor = Executor.newInstance(HttpClients.custom().setSSLContext(createFakeSSLContext()).build());
            IRenderClient renderClient = new PlainRenderClient(executor, medicationServiceRenderUrl);

            Map<String, String> xHeaders = Map.of(X_INSURANT_ID, "Z123456789", X_USER_AGENT, "CLIENTID1234567890AB/2.1.12-45");
            File file = renderClient.getPdfFile(xHeaders);
            assertTrue(file.exists());

            byte[] xhtmlDocument = renderClient.getXhtmlDocument(xHeaders);
            assertTrue(new String(xhtmlDocument).contains("Verordnungsdatum"));
        }
    }
}
