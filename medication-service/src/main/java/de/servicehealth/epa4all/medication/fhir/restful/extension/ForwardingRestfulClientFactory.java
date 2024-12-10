package de.servicehealth.epa4all.medication.fhir.restful.extension;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.IHttpClient;

public class ForwardingRestfulClientFactory extends ApacheRestfulClientFactory {

    private final String konnektor;

    public ForwardingRestfulClientFactory(String konnektor, FhirContext ctx) {
        super(ctx);
        this.konnektor = konnektor;
    }

    @Override
    public synchronized GenericForwardingClient newGenericClient(String theServerBase) {
        validateConfigured();
        IHttpClient httpClient = getHttpClient(theServerBase);
        return new GenericForwardingClient(konnektor, getFhirContext(), httpClient, theServerBase, this);
    }
}
