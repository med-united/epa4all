package de.servicehealth.epa4all.medication.service;

import ca.uhn.fhir.context.FhirContext;
import de.servicehealth.epa4all.medication.config.EpaMedicationConfig;
import de.servicehealth.epa4all.medication.fhir.restful.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.PlainRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.VauRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.factory.VauRestfulClientFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.apache.http.client.fluent.Executor;
import org.apache.http.impl.client.HttpClients;

import java.net.URI;

import static de.servicehealth.utils.TransportUtils.createFakeSSLContext;

@ApplicationScoped
public class MedicationClientProvider {

    @Inject
    EpaMedicationConfig epaMedicationConfig;

    @Produces
    public IRenderClient getRenderClient() throws Exception {
        String medicationServiceRenderUrl = epaMedicationConfig.getMedicationServiceRenderUrl();
        boolean proxy = epaMedicationConfig.isProxy();
        if (proxy) {
            FhirContext ctx = FhirContext.forR4();
            Executor executor = VauRestfulClientFactory.applyToFhirContext(ctx, getBaseUrl(medicationServiceRenderUrl));
            return new VauRenderClient(executor, medicationServiceRenderUrl);
        } else {
            Executor executor = Executor.newInstance(HttpClients.custom().setSSLContext(createFakeSSLContext()).build());
            return new PlainRenderClient(executor, medicationServiceRenderUrl);
        }
    }

    @Produces
    public IMedicationClient getMedicationClient() throws Exception {
        FhirContext ctx = FhirContext.forR4();
        String medicationServiceApiUrl = epaMedicationConfig.getMedicationServiceApiUrl();
        boolean proxy = epaMedicationConfig.isProxy();
        if (proxy) {
            VauRestfulClientFactory.applyToFhirContext(ctx, getBaseUrl(medicationServiceApiUrl));
            return ctx.newRestfulClient(IMedicationClient.class, medicationServiceApiUrl);
        } else {
            return ctx.newRestfulClient(IMedicationClient.class, medicationServiceApiUrl);
        }
    }

    private String getBaseUrl(String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme() + "://";
        String host = uri.getHost();
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return scheme + host + port;
    }
}
