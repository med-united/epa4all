package de.servicehealth.epa4all.medication.fhir.interceptor;

import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.api.IHttpResponse;

import java.util.HashMap;
import java.util.Map;

public class XHeadersInterceptor implements IClientInterceptor {

    private final Map<String, Object> xHeaders;

    public XHeadersInterceptor(Map<String, Object> xHeaders) {
        this.xHeaders = new HashMap<>(xHeaders == null ? Map.of() : xHeaders);
    }

    @Override
    public void interceptRequest(IHttpRequest theRequest) {
        xHeaders.forEach((key, value) ->
            theRequest.addHeader(key, (String) value)
        );
    }

    @Override
    public void interceptResponse(IHttpResponse theResponse) {
    }
}
