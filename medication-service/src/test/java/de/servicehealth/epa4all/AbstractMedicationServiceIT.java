package de.servicehealth.epa4all;

import de.gematik.vau.lib.VauClientStateMachine;
import de.servicehealth.vau.VauClient;
import jakarta.inject.Inject;
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

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static de.servicehealth.epa4all.medication.fhir.restful.extension.AbstractRenderClient.PDF_EXT;

public abstract class AbstractMedicationServiceIT {

    public static final String MEDICATION_SERVICE = "medication-service";

    @Inject
    @ConfigProperty(name = "medication-service.api.url")
    String medicationServiceApiUrl;

    @Inject
    @ConfigProperty(name = "medication-service.render.url")
    String medicationServiceRenderUrl;

    protected VauClient vauClient = new VauClient(new VauClientStateMachine());

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeEach
    public void before() {
        Stream.of(Objects.requireNonNull(
            new File(".").listFiles((dir, name) -> name.endsWith(PDF_EXT)))
        ).forEach(File::delete);
    }

    protected Medication prepareMedication() {
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
