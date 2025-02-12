package de.servicehealth.vau;

import de.servicehealth.registry.BeanRegistry;
import io.vertx.core.impl.ConcurrentHashSet;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
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

    private static final Logger log = LoggerFactory.getLogger(VauFacade.class.getName());

    public static final String NO_USER_SESSION = "no userSession";
    public static final String ACCESS_DENIED = "accessDenied";
    public static final String INVAL_AUTH = "invalAuth";

    public static final Set<String> AUTH_ERRORS = Set.of(
        NO_USER_SESSION,
        ACCESS_DENIED,
        INVAL_AUTH
    );

    @Inject
    Event<VauSessionReload> vauSessionReloadEvent;

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

    @Setter
    @Getter
    private String backend;

    @Getter
    private String vauNpStatus = "not initialized";

    @Getter
    private final boolean tracingEnabled;

    private final ScheduledExecutorService executorService;
    private final BeanRegistry registry;

    @Inject
    public VauFacade(BeanRegistry registry, VauConfig vauConfig) {
        this.registry = registry;
        this.registry.register(this);
        tracingEnabled = vauConfig.isTracingEnabled();
        int vauReadTimeoutMs = vauConfig.getVauReadTimeoutMs();
        for (int i = 0; i < vauConfig.getVauPoolSize(); i++) {
            vauClients.add(new VauClient(vauConfig.isPu(), vauConfig.isMock(), vauReadTimeoutMs));
        }
        executorService = Executors.newSingleThreadScheduledExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            terminateExecutor(executorService, "Vau-Release-Job", 6000))
        );
        executorService.scheduleWithFixedDelay(() -> {
            try {
                for (VauClient vauClient : vauClients) {
                    Long hangsTime = vauClient.hangs();
                    if (hangsTime != null) {
                        String vauCid = vauClient.forceRelease(hangsTime);
                        String threadName = Thread.currentThread().getName();
                        log.warn(String.format("[%s] Timeout force release CID=%s", threadName, vauCid));
                    }
                }
            } catch (Throwable t) {
                log.error("Error while VauClient forceRelease", t);
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void cleanup() {
        registry.unregister(this);
    }

    public void setVauNpSessionStatus(String vauNpStatus) {
        this.vauNpStatus = vauNpStatus;
    }

    public VauClient acquireVauClient() throws InterruptedException {
        Optional<VauClient> vauClientOpt;
        while (true) {
            vauClientOpt = vauClients.stream()
                .filter(VauClient::acquire)
                .findFirst();
            if (vauClientOpt.isEmpty()) {
                String threadName = Thread.currentThread().getName();
                log.warn(String.format("[%s] WAITING FOR VAU CLIENT ********", threadName));
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

    public void handleVauSession(String vauCid, boolean noUserSession, boolean decrypted) {
        if (noUserSession) {
            vauSessionReloadEvent.fireAsync(new VauSessionReload(backend));
        }
        VauClient vauClient = getVauClient(vauCid);
        if (vauClient != null && !decrypted) {
            log.warn(String.format("[%s] Error force release CID=%s", Thread.currentThread().getName(), vauCid));
            vauClient.forceRelease(null);
        }
    }
}
