package de.servicehealth.epa4all.medication.fhir.restful.extension.render;

import ca.uhn.fhir.rest.api.MethodOutcome;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IMedicationClient;
import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Patient;

import java.util.List;
import java.util.Map;

public class StubMedicationClient implements IMedicationClient {

    @Override
    public IMedicationClient withXHeaders(Map<String, String> xHeaders) {
        throw new NotImplementedException("[Stub] IMedicationClient withXHeaders");
    }

    @Override
    public IMedicationClient withKvnr(String kvnr) {
        throw new NotImplementedException("[Stub] IMedicationClient withKvnr");
    }

    @Override
    public MethodOutcome createResource(IBaseResource resource) {
        throw new NotImplementedException("[Stub] IMedicationClient createResource");
    }

    @Override
    public List<Patient> searchPatients(String kvnr) {
        throw new NotImplementedException("[Stub] IMedicationClient searchPatients");
    }

    @Override
    public List<Medication> searchMedications() {
        throw new NotImplementedException("[Stub] IMedicationClient searchMedications");
    }
}
