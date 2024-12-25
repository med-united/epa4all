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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Dependent
public class VauFacade {

    private static final Logger log = LoggerFactory.getLogger(VauFacade.class);

    public static void terminateExecutor(ExecutorService executorService, String executorName, int awaitMillis) {
        if (executorService != null) {
            log.info(String.format("[%s] Terminating", executorName));
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(awaitMillis, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(awaitMillis, TimeUnit.MILLISECONDS)) {
                        log.info(String.format("[%s] Is not terminated", executorName));
                    }
                }
            } catch (InterruptedException ex) {
                executorService.shutdownNow();
               Thread.currentThread().interrupt();
            }
        }
    }

    @Getter
    private final Set<VauClient> vauClients = new ConcurrentHashSet<>();

    private final ScheduledExecutorService executorService;

    @Setter
    @Getter
    private String backend;

    @Getter
    private String vauNpStatus;

    @Getter
    private final boolean tracingEnabled;
    private final BeanRegistry registry;

    @Getter
    private volatile boolean vauNpSet;

    @Inject
    public VauFacade(BeanRegistry registry, VauConfig vauConfig) {
        this.registry = registry;
        this.registry.register(this);
        tracingEnabled = vauConfig.isTracingEnabled();
        int vauReadTimeoutMs = vauConfig.getVauReadTimeoutSec() * 1000;
        for (int i = 0; i < vauConfig.getVauPoolSize(); i++) {
            VauClientStateMachine vauStateMachine = new VauClientStateMachine(vauConfig.isPu());
            vauClients.add(new VauClient(vauStateMachine, vauConfig.isMock(), vauReadTimeoutMs));
        }
        executorService = Executors.newSingleThreadScheduledExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            terminateExecutor(executorService, "Vau-Release-Job", 6000))
        );
        executorService.scheduleWithFixedDelay(() -> {
            try {
                for (VauClient vauClient : vauClients) {
                    if (vauClient.hangs()) {
                        String vauCid = vauClient.getVauInfo().getVauCid();
                        vauClient.forceRelease();
                        log.warn(String.format("[%s] VauClient is force released", vauCid));
                    }
                }
            } catch (Throwable t) {
                log.error("Error while VauClient forceRelease", t);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    @PreDestroy
    void cleanup() {
        registry.unregister(this);
    }

    public void setVauNpStatus(String vauNpStatus, boolean vauNpSet) {
        this.vauNpStatus = vauNpStatus;
        this.vauNpSet = vauNpSet;
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
