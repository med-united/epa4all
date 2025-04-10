package de.servicehealth.epa4all.server.check;

import de.health.service.check.Check;
import de.health.service.check.Status;
import de.health.service.config.api.IRuntimeConfig;
import de.servicehealth.epa4all.server.status.ServerStatus;
import de.servicehealth.epa4all.server.status.StatusService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class StatusCheck implements Check {

    @Inject
    StatusService statusService;

    @Override
    public String getName() {
        return STATUS_CHECK;
    }

    @Override
    public Status getStatus(IRuntimeConfig runtimeConfig) {
        try {
            ServerStatus serverStatus = statusService.getStatus();
            boolean ok = serverStatus.isCautReadable()
                && serverStatus.isConnectorReachable()
                && serverStatus.isIdpReachable()
                && serverStatus.isEhbaAvailable()
                && serverStatus.isFachdienstReachable()
                && serverStatus.isSmcbAvailable()
                && serverStatus.isIdpaccesstokenObtainable();
            return ok ? Status.Up200 : Status.Down503;
        } catch (Throwable e) {
            return Status.Down500;
        }
    }

    @Override
    public Map<String, Object> getData(IRuntimeConfig runtimeConfig) {
        ServerStatus serverStatus = statusService.getStatus();
        Map<String, Object> map = new HashMap<>();
        map.put("cautReadable", String.valueOf(serverStatus.isCautReadable()));
        map.put("cautInformation", serverStatus.getCautInformation());
        map.put("comfortSignatureAvailable", String.valueOf(serverStatus.isComfortSignatureAvailable()));
        map.put("connectorReachable", String.valueOf(serverStatus.isConnectorReachable()));
        map.put("connectorInformation", serverStatus.getConnectorInformation());
        map.put("ehbaAvailable", String.valueOf(serverStatus.isEhbaAvailable()));
        map.put("ehbaInformation", serverStatus.getEhbaInformation());
        map.put("fachdienstReachable", String.valueOf(serverStatus.isFachdienstReachable()));
        map.put("fachdienstInformation", serverStatus.getFachdienstInformation());
        map.put("idpReachable", String.valueOf(serverStatus.isIdpReachable()));
        map.put("idpInformation", serverStatus.getIdpInformation());
        map.put("idpaccesstokenObtainable", String.valueOf(serverStatus.isIdpaccesstokenObtainable()));
        map.put("smcbAvailable", String.valueOf(serverStatus.isSmcbAvailable()));
        map.put("smcbInformation", serverStatus.getSmcbInformation());
        return map;
    }
}
