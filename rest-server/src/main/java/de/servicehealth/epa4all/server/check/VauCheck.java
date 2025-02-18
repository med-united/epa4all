package de.servicehealth.epa4all.server.check;

import de.health.service.check.Check;
import de.health.service.check.Status;
import de.health.service.config.api.IRuntimeConfig;
import de.servicehealth.registry.BeanRegistry;
import de.servicehealth.vau.VauFacade;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.stream.Collectors;

import static de.health.service.check.Status.Down503;
import static de.health.service.check.Status.Up200;

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
        boolean failedSessions = registry.getInstances(VauFacade.class).stream()
            .anyMatch(f -> f.getStatus().toString().contains("ERROR"));
        return failedSessions ? Down503 : Up200;
    }

    @Override
    public Map<String, Object> getData(IRuntimeConfig runtimeConfig) {
        return registry.getInstances(VauFacade.class).stream().map(f -> {
            String backend = f.getBackend();
            return Pair.of(backend, f.getStatus());
        }).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }
}
