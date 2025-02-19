package de.servicehealth.vau;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Streams;
import de.servicehealth.registry.BeanRegistry;
import io.vertx.core.impl.ConcurrentHashSet;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static de.servicehealth.utils.ServerUtils.createObjectNode;
import static java.util.stream.Collectors.toMap;

@SuppressWarnings("CdiInjectionPointsInspection")
@Dependent
public class VauFacade {

    private static final Logger log = LoggerFactory.getLogger(VauFacade.class.getName());

    public static final String NO_USER_SESSION = "no userSession";
    public static final String NOT_AUTHORIZED = "not authorized";
    public static final String ACCESS_DENIED = "accessDenied";
    public static final String INVAL_AUTH = "invalAuth";

    public static final Set<String> AUTH_ERRORS = Set.of(
        NO_USER_SESSION,
        NOT_AUTHORIZED,
        ACCESS_DENIED,
        INVAL_AUTH
    );

    @Inject
    Event<ReloadEmptySessions> reloadEmptySessionsEvent;

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

    private final Set<VauClient> vauClients = new ConcurrentHashSet<>();

    @Setter
    @Getter
    private String backend;

    @Getter
    private final boolean tracingEnabled;

    private final ScheduledExecutorService executorService;
    private final BeanRegistry registry;

    @Inject
    public VauFacade(
        VauConfig vauConfig,
        BeanRegistry registry,
        @Konnektors Set<String> konnektors
    ) {
        this.registry = registry;
        this.registry.register(this);
        tracingEnabled = vauConfig.isTracingEnabled();

        konnektors.forEach(konnektorWorkplace -> {
            for (int i = 0; i < vauConfig.getVauPoolSize(); i++) {
                vauClients.add(new VauClient(
                    vauConfig.isPu(), vauConfig.isMock(), vauConfig.getVauReadTimeoutMs(), konnektorWorkplace
                ));
            }
        });

        executorService = Executors.newSingleThreadScheduledExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            terminateExecutor(executorService, "Vau-Release-Job", 6000))
        );
        executorService.scheduleWithFixedDelay(() -> {
            try {
                for (VauClient vauClient : getSessionClients()) {
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

    public List<VauClient> getSessionClients() {
        return vauClients.stream().filter(vc ->
            vc.getVauNp() != null && vc.getVauInfo() != null
        ).toList();
    }

    public List<VauClient> getEmptyClients() {
        return vauClients.stream().filter(vc -> vc.getVauInfo() == null).toList();
    }

    public List<VauClient> getEmptyClients(String konnektorWorkplace) {
        return vauClients.stream()
            .filter(vc -> vc.getKonnektorWorkplace().equalsIgnoreCase(konnektorWorkplace))
            .filter(vc -> vc.getVauInfo() == null)
            .toList();
    }

    public VauClient acquire(String konnektor, String workplace) throws InterruptedException {
        Optional<VauClient> vauClientOpt;
        while (true) {
            vauClientOpt = vauClients.stream()
                .filter(vc -> vc.getVauNp() != null && vc.getVauInfo() != null)
                .filter(vc -> konnektor == null || vc.getKonnektorWorkplace().contains(konnektor))
                // todo uncomment when KonnektorDefaultConfig is gone
                // .filter(vc -> workplace == null || vc.getKonnektorWorkplace().contains(workplace))
                .filter(VauClient::acquire).findFirst();
            if (vauClientOpt.isEmpty()) {
                log.warn("WAITING FOR VAU CLIENT ********");
                TimeUnit.MILLISECONDS.sleep(300);
            } else {
                break;
            }
        }
        return vauClientOpt.get();
    }

    public VauClient acquire(String vauUuid) {
        Optional<VauClient> vauClientOpt = vauClients.stream()
            .filter(vc -> vc.getUuid().equals(vauUuid))
            .findFirst();
        if (vauClientOpt.isEmpty()) {
            throw new IllegalStateException(String.format("VauClient not found by uuid=%s", vauUuid));
        }
        VauClient vauClient = vauClientOpt.get();
        if (vauClient.acquire()) {
            return vauClient;
        } else {
            throw new IllegalStateException(String.format("VauClient uuid=%s acquired by UUID", vauClient.getUuid()));
        }
    }

    public VauClient get(String vauUuid) {
        return vauClients.stream()
            .filter(vc -> vc.getUuid().equals(vauUuid))
            .findFirst()
            .orElse(null);
    }

    public VauClient find(String vauCid) {
        return vauClients.stream()
            .filter(vc -> vc.getVauInfo() != null && vc.getVauInfo().getVauCid().equals(vauCid))
            .findFirst()
            .orElse(null);
    }

    public void handleVauSessionError(String vauCid, boolean noUserSession, boolean decrypted) {
        boolean vauStateMachineDiscrepancy = !decrypted;
        if (noUserSession || vauStateMachineDiscrepancy) {
            VauClient vauClient = find(vauCid);
            if (vauClient != null) {
                log.warn(String.format("Error force release CID=%s", vauCid));
                vauClient.forceRelease(null);
            }
            reloadEmptySessionsEvent.fireAsync(new ReloadEmptySessions(backend));
        }
    }

    public JsonNode getStatus() {
        Map<String, String> map = Streams.concat(
            getSessionClients().stream().map(vc -> Pair.of(vc.getUuid(), "CONNECTION OK")),
            getEmptyClients().stream().map(vc -> Pair.of(vc.getUuid(), "CONNECTION ERROR"))
        ).collect(toMap(Pair::getKey, Pair::getValue));
        return createObjectNode(map);
    }
}
