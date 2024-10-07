package de.servicehealth.epa4all.medication.fhir.restful.factory;

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
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.fluent.Executor;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static de.servicehealth.epa4all.TransportUtils.createFakeSSLContext;

public class VauRestfulClientFactory extends ApacheRestfulClientFactory {

    public static Executor applyToFhirContext(FhirContext ctx, String medicationServiceBaseUrl) throws Exception {
        VauRestfulClientFactory vauClientFactory = new VauRestfulClientFactory(ctx);
        return Executor.newInstance(vauClientFactory.initVauHttpClient(ctx, medicationServiceBaseUrl));
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
        CloseableHttpClient vauHttpClient = buildHttpClient(sslContext, requestInterceptor, responseInterceptor);

        setHttpClient(vauHttpClient); // used for getNativeHttpClient()
        setServerValidationMode(ServerValidationModeEnum.NEVER);

        return vauHttpClient;
    }

    @SuppressWarnings("deprecation")
    private CloseableHttpClient buildHttpClient(
        SSLContext sslContext,
        FHIRRequestVAUInterceptor requestInterceptor,
        FHIRResponseVAUInterceptor responseInterceptor
    ) {
        // taken from getNativeHttpClient.getNativeHttpClient()
        RequestConfig defaultRequestConfig = RequestConfig.custom()
            .setSocketTimeout(getSocketTimeout())
            .setConnectTimeout(getConnectTimeout())
            .setConnectionRequestTimeout(getConnectionRequestTimeout())
            .setStaleConnectionCheckEnabled(true)
            .build();

        HttpClientBuilder builder = getHttpClientBuilder()
            .useSystemProperties()
            .setDefaultRequestConfig(defaultRequestConfig)
            .addInterceptorFirst(requestInterceptor)
            .addInterceptorLast(responseInterceptor)
            .setSSLContext(sslContext)
            .disableCookieManagement();

        PoolingHttpClientConnectionManager connectionManager =
            new PoolingHttpClientConnectionManager(5000, TimeUnit.MILLISECONDS);
        connectionManager.setMaxTotal(getPoolMaxTotal());
        connectionManager.setDefaultMaxPerRoute(getPoolMaxPerRoute());

        builder.setConnectionManager(connectionManager);
        return builder.build();
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