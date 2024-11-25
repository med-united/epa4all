package de.servicehealth.epa4all.medication.fhir.restful.extension;

import org.apache.http.Header;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;

import java.net.URI;
import java.util.Map;

public class PlainRenderClient extends AbstractRenderClient {

    public PlainRenderClient(Executor executor, String medicationServiceRenderUrl, Map<String, Object> xHeaders) {
        super(executor, medicationServiceRenderUrl, xHeaders);
    }

    @Override
    protected Request buildRequest(URI renderUri, Header[] headers) {
        return Request.Get(renderUri).setHeaders(headers);
    }
}
