package de.servicehealth.epa4all.server.epa;

import de.health.service.cetp.retry.Retrier;
import de.servicehealth.vau.VauConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryError;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryErrorList;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.ehcache.impl.internal.concurrent.ConcurrentHashMap;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static de.servicehealth.api.epa4all.EpaMultiService.EPA_RECORD_IS_NOT_FOUND;
import static de.servicehealth.vau.VauClient.VAU_NO_SESSION;
import static de.servicehealth.vau.VauFacade.SOAP_INVAL_AUTH;

@ApplicationScoped
public class EpaCallGuard {

    private final ConcurrentHashMap<String, Boolean> blockedBackends = new ConcurrentHashMap<>();

    private final VauConfig vauConfig;

    @Inject
    public EpaCallGuard(VauConfig vauConfig) {
        this.vauConfig = vauConfig;
    }

    public void setBlocked(String backend, boolean value) {
        blockedBackends.put(backend, value);
    }

    public <T extends RegistryResponseType> T callAndRetry(String backend, XdsResponseAction<T> action) throws Exception {
        return Retrier.callAndRetryEx(
            vauConfig.getVauCallRetries(),
            vauConfig.getVauCallRetryPeriodMs(),
            true,
            Set.of(EPA_RECORD_IS_NOT_FOUND),
            action::execute,
            () -> blockedBackends.get(backend),
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

    public Response callAndRetry(String backend, ResponseAction action) throws Exception {
        return Retrier.callAndRetryEx(
            vauConfig.getVauCallRetries(),
            vauConfig.getVauCallRetryPeriodMs(),
            true,
            Set.of(EPA_RECORD_IS_NOT_FOUND, "Die eGK hat bereits eine Kartensitzung", "Pin-Status: VERIFIABLE"),
            action::execute,
            () -> blockedBackends.get(backend),
            response -> {
                String header = response.getHeaderString(VAU_NO_SESSION);
                return header == null;
            }
        );
    }

    public <T> T callAndRetry(KonnektorAction<T> action) throws Exception {
        return Retrier.callAndRetryEx(
            List.of(500),
            1500,
            true,
            Set.of(),
            action::execute,
            () -> null,
            Objects::nonNull
        );
    }
}
