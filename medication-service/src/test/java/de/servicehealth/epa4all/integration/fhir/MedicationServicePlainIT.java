package de.servicehealth.epa4all.integration.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.servicehealth.epa4all.common.profile.PlainLocalTestProfile;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.IRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.PlainRenderClient;
import de.servicehealth.folder.IFolderService;
import de.servicehealth.utils.ServerUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.client.fluent.Executor;
import org.apache.http.impl.client.HttpClients;
import org.hl7.fhir.r4.model.Medication;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

import static de.servicehealth.epa4all.common.TestUtils.isDockerContainerRunning;
import static de.servicehealth.utils.SSLUtils.createFakeSSLContext;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(PlainLocalTestProfile.class)
public class MedicationServicePlainIT extends AbstractMedicationServiceIT {

    private final static Logger log = LoggerFactory.getLogger(MedicationServicePlainIT.class.getName());

    @Test
    public void medicationSemiCRUDWorks() {
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

            IFolderService folderService = new IFolderService() {
                @Override
                public File getRootFolder() {
                    return null;
                }

                @Override
                public void writeBytesToFile(String telematikId, byte[] bytes, File outFile) {
                    try {
                        ServerUtils.writeBytesToFile(bytes, outFile);
                    } catch (IOException e) {
                        log.error("Error while saving file: " + outFile.getAbsolutePath(), e);
                    }
                }

                @Override
                public Supplier<File> getTelematikFolderSupplier(String telematikId) {
                    return null;
                }
            };

            Executor executor = Executor.newInstance(HttpClients.custom().setSSLContext(createFakeSSLContext()).build());
            IRenderClient renderClient = new PlainRenderClient(executor, epaUserAgent, medicationServiceRenderUrl, folderService);

            Map<String, String> xHeaders = Map.of(X_INSURANT_ID, "Z123456789", X_USER_AGENT, "CLIENTID1234567890AB/2.1.12-45");
            File file = renderClient.getPdfFile(null, xHeaders);
            assertTrue(file.exists());

            byte[] xhtmlDocument = renderClient.getXhtmlDocument(xHeaders);
            assertTrue(new String(xhtmlDocument).contains("Verordnungsdatum"));
        }
    }
}