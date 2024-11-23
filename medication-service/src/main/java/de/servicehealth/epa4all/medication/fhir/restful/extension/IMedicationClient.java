package de.servicehealth.epa4all.medication.fhir.restful.extension;

import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.r4.model.Patient;

import java.util.List;

public interface IMedicationClient {

    MethodOutcome createPatient(Patient patient);

    List<Patient> searchPatients(String kvnr);
}
