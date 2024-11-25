package de.servicehealth.vau;

import de.gematik.vau.lib.VauClientStateMachine;
import de.servicehealth.registry.BeanRegistry;
import io.vertx.core.impl.ConcurrentHashSet;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static de.servicehealth.utils.ServerUtils.terminateExecutor;

@Dependent
public class VauFacade {

    private static final Logger log = LoggerFactory.getLogger(VauFacade.class);

    @Getter
    private final Set<VauClient> vauClients = new ConcurrentHashSet<>();
    
    private final ScheduledExecutorService executorService;

    @Setter
    @Getter
    private String backend;

    @Getter
    private final boolean tracingEnabled;
    private final BeanRegistry registry;

    @Inject
    public VauFacade(BeanRegistry registry, VauConfig vauConfig) {
        this.registry = registry;
        this.registry.register(this);
        tracingEnabled = vauConfig.isTracingEnabled();
        for (int i = 0; i < vauConfig.getVauPoolSize(); i++) {
            vauClients.add(new VauClient(new VauClientStateMachine(vauConfig.isPu())));
        }
        executorService = Executors.newSingleThreadScheduledExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            terminateExecutor(executorService, "Vau-Release-Job", 6000))
        );
        int vauReadTimeoutMs = vauConfig.getVauReadTimeoutSec() * 1000;
        executorService.scheduleWithFixedDelay(() -> {
            for(VauClient vauClient: vauClients) {
                if (vauClient.busy() && vauClient.getAcquiredAt().get() < System.currentTimeMillis() - vauReadTimeoutMs) {
                    log.warn(String.format("VauClient [VAU_CID='%s'] is force released", vauClient.getVauInfo().getVauCid()));
                    vauClient.forceRelease();
                }
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    @PreDestroy
    void cleanup() {
        registry.unregister(this);
    }

    public VauClient acquireVauClient() throws InterruptedException {
        Optional<VauClient> vauClientOpt;
        while (true) {
            vauClientOpt = vauClients.stream()
                .filter(VauClient::acquire)
                .findFirst();
            if (vauClientOpt.isEmpty()) {
                log.info(String.format("******** [%s] WAITING FOR VAU CLIENT ********", Thread.currentThread().getName()));
                TimeUnit.MILLISECONDS.sleep(300);
            } else {
                break;
            }
        }
        return vauClientOpt.get();
    }

    public VauClient getVauClient(String vauCid) {
        return vauClients.stream()
            .filter(vc -> vc.getVauInfo() != null)
            .filter(vc -> vc.getVauInfo().getVauCid().equals(vauCid))
            .findFirst()
            .orElse(null);
    }
}
