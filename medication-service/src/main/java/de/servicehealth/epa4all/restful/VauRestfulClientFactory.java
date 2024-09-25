package de.servicehealth.epa4all.restful;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.Header;
import ca.uhn.fhir.rest.client.api.IHttpClient;

import java.util.List;
import java.util.Map;

public class VauRestfulClientFactory extends ApacheRestfulClientFactory {

    public VauRestfulClientFactory(FhirContext theContext) {
        super(theContext);
    }

    @Override
    public synchronized IHttpClient getHttpClient(
        StringBuilder theUrl, Map<String, List<String>> theIfNoneExistParams, String theIfNoneExistString,
        RequestTypeEnum theRequestType, List<Header> theHeaders
    ) {
        return new VauApacheHttpClient(
            getNativeHttpClient(), theUrl, theIfNoneExistParams, theIfNoneExistString, theRequestType, theHeaders
        );
    }
}