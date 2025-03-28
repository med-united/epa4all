package de.servicehealth.epa4all.medication.fhir.restful.extension;

import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Patient;

import java.util.List;
import java.util.Map;

public interface IMedicationClient {

    IMedicationClient withXHeaders(Map<String, String> xHeaders);

    IMedicationClient withKvnr(String kvnr);

    MethodOutcome createResource(IBaseResource resource);

    List<Patient> searchPatients(String kvnr);

    List<Medication> searchMedications();
}
