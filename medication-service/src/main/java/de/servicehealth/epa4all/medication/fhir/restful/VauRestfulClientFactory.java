package de.servicehealth.epa4all.medication.fhir.restful;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.Header;
import ca.uhn.fhir.rest.client.api.IHttpClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import de.gematik.vau.lib.VauClientStateMachine;
import de.servicehealth.epa4all.VauClient;
import de.servicehealth.epa4all.medication.fhir.interceptor.FHIRRequestVAUInterceptor;
import de.servicehealth.epa4all.medication.fhir.interceptor.FHIRResponseVAUInterceptor;
import org.apache.http.client.fluent.Executor;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static de.servicehealth.epa4all.TransportUtils.createFakeSSLContext;
import static org.apache.http.client.fluent.Executor.newInstance;

public class VauRestfulClientFactory extends ApacheRestfulClientFactory {

    public static Executor applyToFhirContext(FhirContext ctx, String medicationServiceBaseUrl) throws Exception {
        VauRestfulClientFactory vauClientFactory = new VauRestfulClientFactory(ctx);
        return newInstance(vauClientFactory.initVauHttpClient(ctx, medicationServiceBaseUrl));
    }

    private VauRestfulClientFactory(FhirContext ctx) {
        super(ctx);
    }

    private CloseableHttpClient initVauHttpClient(FhirContext ctx, String medicationServiceBaseUrl) throws Exception {
        ctx.setRestfulClientFactory(this);

        SSLContext sslContext = createFakeSSLContext();
        URI medicationBaseUri = URI.create(medicationServiceBaseUrl);

        VauClient vauClient = new VauClient(new VauClientStateMachine());
        FHIRRequestVAUInterceptor requestInterceptor = new FHIRRequestVAUInterceptor(medicationBaseUri, sslContext, vauClient);
        FHIRResponseVAUInterceptor responseInterceptor = new FHIRResponseVAUInterceptor(vauClient);

        CloseableHttpClient vauHttpClient = HttpClients.custom()
            .addInterceptorFirst(requestInterceptor)
            .addInterceptorLast(responseInterceptor)
            .setSSLContext(sslContext)
            .build();

        setHttpClient(vauHttpClient); // used for getNativeHttpClient()
        setServerValidationMode(ServerValidationModeEnum.NEVER);

        return vauHttpClient;
    }

    @Override
    public synchronized IHttpClient getHttpClient(
        StringBuilder theUrl, Map<String, List<String>> theIfNoneExistParams, String theIfNoneExistString,
        RequestTypeEnum theRequestType, List<Header> theHeaders
    ) {
        return new VauApacheHttpClient(
            getNativeHttpClient(),
            theUrl,
            theIfNoneExistParams,
            theIfNoneExistString,
            theRequestType,
            theHeaders
        );
    }
}