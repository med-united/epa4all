package de.servicehealth.epa4all.medication.fhir.restful.extension.render;

import org.apache.http.Header;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;

import java.net.URI;

public class PlainRenderClient extends AbstractRenderClient {

    public PlainRenderClient(Executor executor, String epaUserAgent, String medicationServiceRenderUrl) {
        super(executor, epaUserAgent, medicationServiceRenderUrl);
    }

    @Override
    protected Request buildRequest(URI renderUri, Header[] headers) {
        return Request.Get(renderUri).setHeaders(headers);
    }
}
