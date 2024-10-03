package de.servicehealth.epa4all.medication.fhir.restful;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.client.api.IRestfulClient;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Medication;

public interface IMedicationClient extends IRestfulClient {

    @Read()
    Medication get(@IdParam IIdType theId);
}