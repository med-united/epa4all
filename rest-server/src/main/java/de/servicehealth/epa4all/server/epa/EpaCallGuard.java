package de.servicehealth.epa4all.server.epa;

import de.health.service.cetp.retry.Retrier;
import de.servicehealth.vau.VauConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryError;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryErrorList;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;

import static de.servicehealth.epa4all.cxf.interceptor.CxfVauReadSoapInterceptor.SOAP_INVAL_AUTH;
import static de.servicehealth.vau.VauClient.VAU_NO_SESSION;

@ApplicationScoped
public class EpaCallGuard {

    @Inject
    VauConfig vauConfig;

    public <T extends RegistryResponseType> T callAndRetry(XdsResponseAction<T> action) throws Exception {
        return Retrier.callAndRetryEx(
            vauConfig.getVauCallRetries(),
            vauConfig.getVauCallRetryPeriodMs(),
            true,
            action::execute,
            registryResponse -> {
                RegistryErrorList errorList = registryResponse.getRegistryErrorList();
                if (errorList == null || errorList.getRegistryError().isEmpty()) {
                    return true;
                }
                RegistryError registryError = errorList.getRegistryError().getFirst();
                return !registryError.getErrorCode().contains(SOAP_INVAL_AUTH);
            }
        );
    }

    public Response callAndRetry(FhirResponseAction action) throws Exception {
        return Retrier.callAndRetryEx(
            vauConfig.getVauCallRetries(),
            vauConfig.getVauCallRetryPeriodMs(),
            true,
            action::execute,
            response -> response.getHeaderString(VAU_NO_SESSION) == null
        );
    }
}
