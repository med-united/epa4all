package de.servicehealth.epa4all;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.BundleUtil;
import de.servicehealth.epa4all.common.ProxyTestProfile;
import de.servicehealth.epa4all.medication.fhir.interceptor.XHeadersInterceptor;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.VauRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.factory.VauRestfulClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.client.fluent.Executor;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static de.servicehealth.epa4all.common.Utils.isDockerServiceRunning;
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
        if (isDockerServiceRunning(MEDICATION_SERVICE)) {

            FhirContext ctx = FhirContext.forR4();
            VauRestfulClientFactory apiClientFactory = new VauRestfulClientFactory(ctx);
            apiClientFactory.init(vauFacade, getBaseUrl(medicationServiceApiUrl));

            String kvnr = "X110485291";

            IGenericClient medicationClient = ctx.newRestfulGenericClient(medicationServiceApiUrl);
            medicationClient.registerInterceptor(new XHeadersInterceptor(Map.of(X_BACKEND, "medication-service:8080")));

            MethodOutcome outcome = medicationClient.create().resource(preparePatient(kvnr)).execute();
            Long id = outcome.getId().getIdPartAsLong();
            assertNotNull(id);

            Bundle bundle = medicationClient.search()
                .forResource(Patient.class)
                .where(Patient.IDENTIFIER.exactly().identifier(kvnr))
                .returnBundle(Bundle.class)
                .execute();
            
            List<IBaseResource> patients = new ArrayList<>(BundleUtil.toListOfResources(ctx, bundle));

            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = medicationClient.loadPage().next(bundle).execute();
                patients.addAll(BundleUtil.toListOfResources(ctx, bundle));
            }

            assertFalse(patients.isEmpty());
            patients.forEach(p -> System.out.println(p.getIdElement()));
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
        if (isDockerServiceRunning(MEDICATION_SERVICE)) {
            FhirContext ctx = FhirContext.forR4();
            VauRestfulClientFactory apiClientFactory = new VauRestfulClientFactory(ctx);
            apiClientFactory.init(vauFacade, getBaseUrl(medicationServiceRenderUrl));

            Executor executor = Executor.newInstance(apiClientFactory.getVauHttpClient());
            IRenderClient renderClient = new VauRenderClient(
                executor,
                medicationServiceRenderUrl,
                Map.of(X_INSURANT_ID, "Z123456789", X_USER_AGENT, "CLIENTID1234567890AB/2.1.12-45")
            );
            
            File file = renderClient.getPdfFile();
            assertTrue(file.exists());

            byte[] xhtmlDocument = renderClient.getXhtmlDocument();
            assertTrue(new String(xhtmlDocument).contains("Verordnungsdatum"));
        }
    }
}
