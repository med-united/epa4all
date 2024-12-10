package de.servicehealth.epa4all.medication.fhir.restful.extension;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.SearchTotalModeEnum;
import ca.uhn.fhir.rest.client.api.IHttpClient;
import ca.uhn.fhir.rest.client.impl.GenericClient;
import ca.uhn.fhir.rest.client.impl.RestfulClientFactory;
import ca.uhn.fhir.rest.gclient.IClientExecutable;
import ca.uhn.fhir.rest.gclient.ICreateTyped;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import ca.uhn.fhir.util.BundleUtil;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Patient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AbstractMedicationClient ancestors don't clear ThreadLocals
 * because they are used for testing purposes only.
 */
public abstract class AbstractMedicationClient extends GenericClient implements IMedicationClient {

    private final FhirContext ctx;

    public AbstractMedicationClient(
        FhirContext theContext,
        IHttpClient theHttpClient,
        String theServerBase,
        RestfulClientFactory theFactory
    ) {
        super(theContext, theHttpClient, theServerBase, theFactory);

        ctx = theContext;
    }

    protected abstract Map<String, String> getXHeaders();

    protected abstract Map<String, String> getXQueryParams();

    @Override
    public IMedicationClient withXHeaders(Map<String, String> xHeaders) {
        return this;
    }

    @Override
    public IMedicationClient withKvnr(String kvnr) {
        return this;
    }

    private <T extends IClientExecutable<T, ?>> void withHeaders(
        IClientExecutable<T, ?> executable,
        Map<String, String> xHeaders
    ) {
        xHeaders.forEach(executable::withAdditionalHeader);
    }

    @Override
    protected Map<String, List<String>> createExtraParams(String theCustomAcceptHeader) {
        Map<String, List<String>> extraParams = super.createExtraParams(theCustomAcceptHeader);
        getXQueryParams().forEach((key, value) -> extraParams.put(key, List.of(value)));
        return extraParams;
    }

    @Override
    public MethodOutcome createResource(IBaseResource resource) {
        ICreateTyped createTyped = create().resource(resource);
        withHeaders(createTyped, getXHeaders());
        return createTyped.execute();
    }

    @Override
    public List<Patient> searchPatients(String kvnr) {
        IQuery<Patient> resource = search().forResource(Patient.class);
        withHeaders(resource, getXHeaders());
        Bundle bundle = resource
            .where(Patient.IDENTIFIER.exactly().identifier(kvnr))
            .returnBundle(Bundle.class)
            .execute();

        return listResources(loadResources(bundle), Patient.class);
    }

    @Override
    public List<Medication> searchMedications() {
        IQuery<Medication> resource = search().forResource(Medication.class);
        withHeaders(resource, getXHeaders());
        Bundle bundle = resource
            .where(new StringClientParam("_offset").matches().value("0"))
            .where(new StringClientParam("_format").matches().value("json"))
            .count(20)
            .totalMode(SearchTotalModeEnum.NONE)
            .returnBundle(Bundle.class)
            .execute();

        return listResources(loadResources(bundle), Medication.class);
    }

    private List<IBaseResource> loadResources(Bundle bundle) {
        List<IBaseResource> resources = new ArrayList<>(BundleUtil.toListOfResources(ctx, bundle));

        // TODO load page
        
        // while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
        //     bundle = loadPage().next(bundle).execute();
        //     resources.addAll(BundleUtil.toListOfResources(ctx, bundle));
        // }
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
