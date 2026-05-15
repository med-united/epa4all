package de.servicehealth.epa4all.integration.fhir;

import de.servicehealth.vau.VauFacade;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.junit.jupiter.api.BeforeEach;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.stream.Stream;

import static de.servicehealth.epa4all.medication.fhir.restful.extension.render.AbstractRenderClient.PDF_EXT;
import static de.servicehealth.utils.SSLUtils.SslContextType.TLS;

public abstract class AbstractMedicationServiceIT {

    public static final String MEDICATION_SERVICE = "medication-service";

    protected String epaUserAgent = "GEMIncenereS2QmFN83P/1.0.0";

    @Inject
    @ConfigProperty(name = "medication-service.api.url")
    String medicationServiceApiUrl;

    @Inject
    @ConfigProperty(name = "medication-service.render.url")
    String medicationServiceRenderUrl;

    @Inject
    VauFacade vauFacade;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeEach
    public void before() {
        Stream.of(Objects.requireNonNull(
            new File(".").listFiles((dir, name) -> name.endsWith(PDF_EXT)))
        ).forEach(File::delete);
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

    /**
     * SSL context backed by the bundled wiremock self-signed CA — replaces the previous
     * trust-all helper. The truststore lives in {@code common-test} resources so any module
     * with that test dep can reach wiremock TLS without needing an in-codebase trust-all path.
     */
    protected SSLContext createTestSSLContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance(TLS.name());
        try (InputStream in = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream("test-truststore/test-truststore.p12")) {
            if (in == null) {
                throw new IllegalStateException("test-truststore/test-truststore.p12 not on classpath "
                    + "(common-test must be a test-scope dependency)");
            }
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, "password".toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
            return sslContext;
        }
    }
}
