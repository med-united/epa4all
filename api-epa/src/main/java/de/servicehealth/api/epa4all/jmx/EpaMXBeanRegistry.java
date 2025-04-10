package de.servicehealth.api.epa4all.jmx;

import de.servicehealth.api.epa4all.EpaConfig;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@ApplicationScoped
public class EpaMXBeanRegistry {

    private static final Logger log = LoggerFactory.getLogger(EpaMXBeanRegistry.class.getName());

    private final Map<String, LongAdder> epaRequestCount = new ConcurrentHashMap<>();

    @Inject
    EpaConfig epaConfig;

    void onStart(@Observes StartupEvent ev) {
        epaConfig.getEpaBackends().forEach(backend -> {
            String qualifier = "_" + backend.replace(":", "_");
            log.info("Registering EpaMXBean" + qualifier);
            epaRequestCount.put(backend, new LongAdder());
            EpaMXBean epaMXBean = () -> epaRequestCount.get(backend).sum();
            EpaMXBeanManager.registerMXBean(epaMXBean, qualifier);
        });
    }

    public void registerRequest(String backend) {
        epaRequestCount.computeIfAbsent(backend, b -> new LongAdder()).increment();
    }
}
