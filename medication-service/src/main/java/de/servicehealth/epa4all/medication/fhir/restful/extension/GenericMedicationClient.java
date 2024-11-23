package de.servicehealth.epa4all.medication.fhir.restful.extension;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.BundleUtil;
import de.servicehealth.epa4all.medication.fhir.interceptor.XHeadersInterceptor;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GenericMedicationClient implements IMedicationClient {

    private final FhirContext ctx;
    private final IGenericClient medicationClient;

    public GenericMedicationClient(FhirContext ctx, String medicationApiUrl, Map<String, Object> runtimeAttributes) {
        this.ctx = ctx;
        medicationClient = ctx.newRestfulGenericClient(medicationApiUrl);
        medicationClient.registerInterceptor(new XHeadersInterceptor(runtimeAttributes));
    }

    @Override
    public MethodOutcome createPatient(Patient patient) {
        return medicationClient.create().resource(patient).execute();
    }

    @Override
    public List<Patient> searchPatients(String kvnr) {
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
        return patients.stream().map(r -> {
            if (r instanceof Patient patient) {
                return patient;
            } else {
                return null;
            }
        }).filter(Objects::nonNull).toList();
    }
}
