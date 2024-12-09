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

import java.io.File;
import java.util.Objects;
import java.util.stream.Stream;

import static de.servicehealth.epa4all.medication.fhir.restful.extension.render.AbstractRenderClient.PDF_EXT;

public abstract class AbstractMedicationServiceIT {

    public static final String MEDICATION_SERVICE = "medication-service";

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
}
