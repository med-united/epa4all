package de.servicehealth.epa4all.medication.fhir.restful.extension;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IHttpClient;
import ca.uhn.fhir.rest.client.impl.RestfulClientFactory;

import java.util.Map;

public class GenericDirectClient extends AbstractMedicationClient {

    private final ThreadLocal<Map<String, String>> headersThreadLocal = new ThreadLocal<>();

    public GenericDirectClient(
        FhirContext theContext,
        IHttpClient theHttpClient,
        String theServerBase,
        RestfulClientFactory theFactory
    ) {
        super(theContext, theHttpClient, theServerBase, theFactory);
    }

    public GenericDirectClient withXHeaders(Map<String, String> xHeaders) {
        headersThreadLocal.set(xHeaders);
        return this;
    }

    @Override
    protected Map<String, String> getXHeaders() {
        return headersThreadLocal.get();
    }

    @Override
    protected Map<String, String> getXQueryParams() {
        return Map.of();
    }
}
