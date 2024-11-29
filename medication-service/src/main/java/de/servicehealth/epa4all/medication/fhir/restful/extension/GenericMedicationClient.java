package de.servicehealth.epa4all.medication.fhir.restful.extension;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.BundleUtil;
import de.servicehealth.epa4all.medication.fhir.interceptor.XHeadersInterceptor;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GenericMedicationClient implements IMedicationClient {

    private final FhirContext ctx;
    private final IGenericClient medicationClient;

    public GenericMedicationClient(FhirContext ctx, String medicationApiUrl, Map<String, Object> xHeaders) {
        this.ctx = ctx;
        medicationClient = ctx.newRestfulGenericClient(medicationApiUrl);
        medicationClient.registerInterceptor(new XHeadersInterceptor(xHeaders));
    }

    @Override
    public MethodOutcome createResource(IBaseResource resource) {
        return medicationClient.create().resource(resource).execute();
    }

    @Override
    public List<Patient> searchPatients(String kvnr) {
        Bundle bundle = medicationClient.search()
            .forResource(Patient.class)
            .where(Patient.IDENTIFIER.exactly().identifier(kvnr))
            .returnBundle(Bundle.class)
            .execute();

        return listResources(loadResources(bundle), Patient.class);
    }

    @Override
    public List<Medication> searchMedications(Patient patient) {
        Bundle bundle = medicationClient.search()
            .forResource(MedicationRequest.class)
            .where(MedicationRequest.PATIENT.hasId("Patient/" + patient.getIdElement().getIdPart()))
            .returnBundle(Bundle.class)
            .execute();

        List<MedicationRequest> medicationRequests = listResources(loadResources(bundle), MedicationRequest.class);
        List<Medication> medications = medicationRequests.stream().map(mr -> {
            Reference medicationReference = mr.getMedicationReference();
            if (medicationReference != null) {
                String medicationId = medicationReference.getReference();
                Bundle b = medicationClient.search().forResource(Medication.class)
                    .where(Medication.RES_ID.exactly().identifier(medicationId.split("/")[1]))
                    .returnBundle(Bundle.class)
                    .execute();

                return listResources(loadResources(b), Medication.class).getLast();
            } else {
                return null;
            }
        }).toList();

        return medications.stream().filter(Objects::nonNull).toList();
    }

    private List<IBaseResource> loadResources(Bundle bundle) {
        List<IBaseResource> resources = new ArrayList<>(BundleUtil.toListOfResources(ctx, bundle));
        while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
            bundle = medicationClient.loadPage().next(bundle).execute();
            resources.addAll(BundleUtil.toListOfResources(ctx, bundle));
        }
        return resources;
    }

    @SuppressWarnings("unchecked")
    private <R extends IBaseResource> List<R> listResources(List<IBaseResource> list, Class<R> clazz) {
        return list.stream().map(r -> {
            if (clazz.isInstance(r)) {
                return (R) r;
            } else {
                return null;
            }
        }).filter(Objects::nonNull).toList();
    }
}
