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
        boolean okSessions = registry.getInstances(VauFacade.class).stream()
            .allMatch(f -> f.getVauNpStatus().contains("OK since"));
        return okSessions ? Status.Up200 : Status.Down503;
    }

    @Override
    public Map<String, String> getData(IRuntimeConfig runtimeConfig) {
        return registry.getInstances(VauFacade.class).stream().map(f -> {
            String backend = f.getBackend();
            String status = f.getVauNpStatus();
            int total = f.getVauClients().size();
            int busy = (int) f.getVauClients().stream().filter(VauClient::busy).count();
            int idle = total - busy;
            return Pair.of(backend, String.format("total=%d busy=%d idle=%d vau-session: %s", total, busy, idle, status));
        }).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }
}
