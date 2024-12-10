package de.servicehealth.epa4all.medication.fhir.restful.extension;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IHttpClient;
import ca.uhn.fhir.rest.client.impl.RestfulClientFactory;

import java.util.Map;

import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;

public class GenericForwardingClient extends AbstractMedicationClient {

    private final String konnektor;

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
    }

    @Override
    protected Map<String, String> getXHeaders() {
        return Map.of();
    }

    public GenericForwardingClient withKvnr(String kvnr) {
        kvnrThreadLocal.set(kvnr);
        return this;
    }

    @Override
    protected Map<String, String> getXQueryParams() {
        return Map.of(
            X_INSURANT_ID, kvnrThreadLocal.get(),
            X_KONNEKTOR, konnektor
        );
    }
}
