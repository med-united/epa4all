package de.servicehealth.epa4all.medication.fhir.restful.extension.forward;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.SummaryEnum;
import ca.uhn.fhir.rest.client.api.IHttpClient;
import ca.uhn.fhir.rest.client.impl.BaseHttpClientInvocation;
import ca.uhn.fhir.rest.client.impl.GenericClient;
import ca.uhn.fhir.rest.client.impl.RestfulClientFactory;
import ca.uhn.fhir.rest.client.method.IClientResponseHandler;
import ca.uhn.fhir.util.BundleUtil;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IMedicationClient;
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
import java.util.Set;

import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;

@SuppressWarnings("unchecked")
public class GenericForwardingClient extends GenericClient implements IMedicationClient {

    private final String konnektor;
    private final FhirContext ctx;

    private final ThreadLocal<String> kvnrThreadLocal = new ThreadLocal<>();

    public GenericForwardingClient(
        String konnektor,
        FhirContext theContext,
        IHttpClient theHttpClient,
        String theServerBase,
        RestfulClientFactory theFactory
    ) {
        super(theContext, theHttpClient, theServerBase, theFactory);

        this.konnektor = konnektor;
        ctx = theContext;
    }

    public GenericForwardingClient withKvnr(String kvnr) {
        kvnrThreadLocal.set(kvnr);
        return this;
    }

    @Override
    public MethodOutcome createResource(IBaseResource resource) {
        return create().resource(resource).execute();
    }

    @Override
    public List<Patient> searchPatients(String kvnr) {
        Bundle bundle = (Bundle) search()
            .forResource(Patient.class)
            .where(Patient.IDENTIFIER.exactly().identifier(kvnr))
            .returnBundle(Bundle.class)
            .execute();

        return listResources(loadResources(bundle), Patient.class);
    }

    @Override
    public List<Medication> searchMedications(Patient patient) {
        Bundle bundle = (Bundle) search()
            .forResource(MedicationRequest.class)
            .where(MedicationRequest.PATIENT.hasId("Patient/" + patient.getIdElement().getIdPart()))
            .returnBundle(Bundle.class)
            .execute();

        List<MedicationRequest> medicationRequests = listResources(loadResources(bundle), MedicationRequest.class);
        List<Medication> medications = medicationRequests.stream().map(mr -> {
            Reference medicationReference = mr.getMedicationReference();
            if (medicationReference != null) {
                String medicationId = medicationReference.getReference();
                Bundle b = (Bundle) search().forResource(Medication.class)
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

        // TODO - modify external links before returning to the client

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

    @Override
    protected Map<String, List<String>> createExtraParams(String theCustomAcceptHeader) {
        Map<String, List<String>> extraParams = super.createExtraParams(theCustomAcceptHeader);

        extraParams.put(X_INSURANT_ID, List.of(kvnrThreadLocal.get()));
        extraParams.put(X_KONNEKTOR, List.of(konnektor));

        return extraParams;
    }

    @Override
    protected <T> T invokeClient(
        FhirContext theContext,
        IClientResponseHandler<T> binding,
        BaseHttpClientInvocation clientInvocation,
        EncodingEnum theEncoding,
        Boolean thePrettyPrint,
        boolean theLogRequestAndResponse,
        SummaryEnum theSummaryMode,
        Set<String> theSubsetElements,
        CacheControlDirective theCacheControlDirective,
        String theCustomAcceptHeader,
        Map<String, List<String>> theCustomHeaders
    ) {
        ForwardingBaseHttpClientInvocation forwardingInvocation = new ForwardingBaseHttpClientInvocation(
            theContext, clientInvocation
        );
        return super.invokeClient(
            theContext,
            binding,
            forwardingInvocation,
            theEncoding,
            thePrettyPrint,
            theLogRequestAndResponse,
            theSummaryMode,
            theSubsetElements,
            theCacheControlDirective,
            theCustomAcceptHeader,
            theCustomHeaders
        );
    }
}
