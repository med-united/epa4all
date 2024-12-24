package de.servicehealth.epa4all.server.check;

import de.health.service.check.Check;
import de.health.service.check.Status;
import de.health.service.config.api.IRuntimeConfig;
import de.servicehealth.registry.BeanRegistry;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class VauCheck implements Check {

    private static final String VAU_CHECK = "VauCheck";

    @Inject
    BeanRegistry registry;

    @Override
    public String getName() {
        return VAU_CHECK;
    }

    @Override
    public Status getStatus(IRuntimeConfig runtimeConfig) {
        boolean hasBrokens = registry.getInstances(VauFacade.class)
            .stream()
            .flatMap(f -> f.getVauClients().stream())
            .anyMatch(VauClient::broken);
        
        return hasBrokens ? Status.Down503 : Status.Up200;
    }

    @Override
    public Map<String, String> getData(IRuntimeConfig runtimeConfig) {
        return registry.getInstances(VauFacade.class).stream().map(f -> {
            String backend = f.getBackend();
            String status = f.getVauNpStatus();
            int total = f.getVauClients().size();
            int busy = (int) f.getVauClients().stream().filter(VauClient::busy).count();
            int broken = (int) f.getVauClients().stream().filter(VauClient::broken).count();
            int idle = total - busy - broken;
            return Pair.of(backend, String.format("total=%d busy=%d broken=%d idle=%d vau-np-status: %s", total, busy, broken, idle, status));
        }).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }
}
