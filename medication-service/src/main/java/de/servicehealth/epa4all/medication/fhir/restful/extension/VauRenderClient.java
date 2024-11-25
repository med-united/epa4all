package de.servicehealth.epa4all.medication.fhir.restful.extension;

import org.apache.http.Header;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;

import java.net.URI;
import java.util.Map;

public class VauRenderClient extends AbstractRenderClient {

    public VauRenderClient(Executor executor, String medicationRenderUrl, Map<String, Object> xHeaders) {
        super(executor, medicationRenderUrl, xHeaders);
    }

    @Override
    protected Request buildRequest(URI renderUri, Header[] headers) {
        /*
         * If we construct Request directly as Request.Get/Request.Post/Etc then it bypasses
         * ApacheHttpClient.createHttpRequest procedure, so for direct construction it
         * should be beared in mind that for VAU we always need Request.Post
         */
        return Request.Post(renderUri).setHeaders(headers);
    }
}
