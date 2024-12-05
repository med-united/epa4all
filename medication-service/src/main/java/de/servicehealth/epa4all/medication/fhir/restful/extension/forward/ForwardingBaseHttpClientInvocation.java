package de.servicehealth.epa4all.medication.fhir.restful.extension.forward;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.client.apache.ApacheHttpRequest;
import ca.uhn.fhir.rest.client.api.BaseHttpRequest;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.impl.BaseHttpClientInvocation;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;

public class ForwardingBaseHttpClientInvocation extends BaseHttpClientInvocation {

    private static final Logger log = Logger.getLogger(ForwardingBaseHttpClientInvocation.class.getName());

    private final BaseHttpClientInvocation originInvocation;

    public ForwardingBaseHttpClientInvocation(
        FhirContext myContext,
        BaseHttpClientInvocation originInvocation
    ) {
        super(myContext);
        this.originInvocation = originInvocation;
    }

    @Override
    public IHttpRequest asHttpRequest(
        String theUrlBase, Map<String, List<String>> theExtraParams, EncodingEnum theEncoding, Boolean thePrettyPrint
    ) {
        IHttpRequest httpRequest = originInvocation.asHttpRequest(theUrlBase, theExtraParams, theEncoding, thePrettyPrint);

        String konnektor = evictParameter(theExtraParams, X_KONNEKTOR);
        String kvnr = evictParameter(theExtraParams, X_INSURANT_ID);

        if (httpRequest instanceof ApacheHttpRequest apacheRequest) {
            String uri = apacheRequest.getUri();
            log.info("[CLIENT] Forwarded HTTP request URI = " + uri);

            return apacheRequest;
        } else if (httpRequest instanceof BaseHttpRequest baseRequest) {
            return baseRequest;
        }
        throw new IllegalArgumentException("Unknown http request");
    }

    private String evictParameter(Map<String, List<String>>  extraParams, String headerName) {
        List<String> params = extraParams.remove(headerName);
        return params == null || params.isEmpty() ? "undefined" : params.getFirst();
    }
}
