package de.servicehealth.vau;

import de.gematik.vau.lib.VauClientStateMachine;
import io.vertx.core.impl.ConcurrentHashSet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
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

    private final Set<VauClient> clients = new ConcurrentHashSet<>();
    private final ScheduledExecutorService executorService;

    @Inject
    public VauFacade(VauConfig vauConfig) {
        for (int i = 0; i < vauConfig.getVauPoolSize(); i++) {
            clients.add(new VauClient(new VauClientStateMachine()));
        }
        executorService = Executors.newSingleThreadScheduledExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            terminateExecutor(executorService, "Vau-Release-Job", 6000))
        );
        int vauReadTimeoutMs = vauConfig.getVauReadTimeoutSec() * 1000;
        executorService.scheduleWithFixedDelay(() -> {
            for(VauClient vauClient: clients) {
                if (vauClient.busy() && vauClient.getAcquiredAt().get() < System.currentTimeMillis() - vauReadTimeoutMs) {
                    log.warn(String.format("VauClient [%s] is force released", vauClient.getVauInfo().getVauCid()));
                    vauClient.forceRelease();
                }
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    public VauClient acquireVauClient() throws InterruptedException {
        Optional<VauClient> vauClientOpt;
        while (true) {
            vauClientOpt = clients.stream()
                .filter(VauClient::acquire)
                .findFirst();
            if (vauClientOpt.isEmpty()) {
                TimeUnit.MILLISECONDS.sleep(300);
            } else {
                break;
            }
        }
        return vauClientOpt.get();
    }

    public VauClient getVauClient(String vauCid) {
        return clients.stream()
            .filter(vc -> vc.getVauInfo().getVauCid().equals(vauCid))
            .findFirst()
            .orElse(null);
    }
}
