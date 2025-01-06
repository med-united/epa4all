package de.servicehealth.epa4all.server.idp.vaunp;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.KonnektorsConfigs;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.cetp.domain.fault.CetpFault;
import de.health.service.cetp.retry.Retrier;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.EpaMultiService;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.startup.StartableService;
import de.servicehealth.vau.VauSessionReload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logmanager.Level;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@SuppressWarnings("CdiInjectionPointsInspection")
@ApplicationScoped
public class VauNpProvider extends StartableService {

    private static final Logger log = Logger.getLogger(VauNpProvider.class.getName());

    private static final String STATUS_TEMPLATE = "[%s] Took %d ms - %s";

    private final Map<VauNpKey, String> vauNpMap = new HashMap<>();
    private final Semaphore semaphore = new Semaphore(1);

    @Inject
    @Setter
    ManagedExecutor scheduledThreadPool;

    IdpClient idpClient;
    EpaMultiService epaMultiService;
    IKonnektorClient konnektorClient;
    KonnektorDefaultConfig konnektorDefaultConfig;

    @Setter
    @Inject
    @KonnektorsConfigs
    Map<String, KonnektorConfig> konnektorsConfigs;


    @Inject
    public VauNpProvider(
        IdpClient idpClient,
        EpaMultiService epaMultiService,
        IKonnektorClient konnektorClient,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.idpClient = idpClient;
        this.epaMultiService = epaMultiService;
        this.konnektorClient = konnektorClient;
        this.konnektorDefaultConfig = konnektorDefaultConfig;
    }

    @Override
    public int getPriority() {
        return VauNpProviderPriority;
    }

    public void onStart() throws Exception {
        List<String> statuses;
        Map<VauNpKey, String> cachedNps = loadVauNps();
        if (!cachedNps.isEmpty() && sameConfigs(cachedNps)) {
            vauNpMap.putAll(cachedNps);
            statuses = cachedNps.keySet().stream()
                .map(s -> String.format(STATUS_TEMPLATE, s.getEpaBackend(), 0, "Restored from cache"))
                .toList();
        } else {
            statuses = reload(Set.of());
        }
        log.info(String.format("VAU sessions status:\n%s", String.join("\n", statuses)));
    }

    private Map<String, KonnektorConfig> getUniqueKonnektorsConfigs() {
        // removing ports
        return konnektorsConfigs.entrySet()
            .stream()
            .map(e -> Pair.of(e.getKey().split("_")[1].split(":")[0], e.getValue()))
            .distinct()
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    private Map<VauNpKey, String> loadVauNps() {
        try {
            VauNpFile vauNpFile = new VauNpFile(configDirectory);
            return vauNpFile.get();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error while loading VauNpFile", e);
            return new HashMap<>();
        }
    }

    private boolean sameConfigs(Map<VauNpKey, String> cachedVauNps) {
        for (Map.Entry<String, KonnektorConfig> entry : getUniqueKonnektorsConfigs().entrySet()) {
            for (String backend : epaMultiService.getEpaBackendMap().keySet()) {
                try {
                    RuntimeConfig runtimeConfig = new RuntimeConfig(
                        konnektorDefaultConfig, entry.getValue().getUserConfigurations()
                    );
                    String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
                    VauNpKey vauNpKey = new VauNpKey(smcbHandle, entry.getKey(), backend);
                    if (!cachedVauNps.containsKey(vauNpKey)) {
                        return false;
                    }
                } catch (CetpFault e) {
                    String msg = String.format("Error while getting SMC-B handle: Konnektor=%s", entry.getKey());
                    log.log(Level.SEVERE, msg, e);
                    return false;
                }
            }
        }
        return true;
    }

    private static class VauNpInfo {
        VauNpKey key;
        String vauNp;
        String status;

        public VauNpInfo(VauNpKey key, String vauNp, String status) {
            this.key = key;
            this.vauNp = vauNp;
            this.status = status;
        }

        boolean hasValue() {
            return key != null && vauNp != null;
        }
    }

    private List<String> collectResults(List<Future<VauNpInfo>> futures) throws Exception {
        List<String> statuses = new ArrayList<>();
        for (Future<VauNpInfo> future : futures) {
            try {
                VauNpInfo vauInfo = future.get(60, TimeUnit.SECONDS);
                if (vauInfo.hasValue()) {
                    vauNpMap.put(vauInfo.key, vauInfo.vauNp);
                }
                statuses.add(vauInfo.status);
            } catch (Exception e) {
                statuses.add(e.getMessage());
            }
        }
        new VauNpFile(configDirectory).update(vauNpMap);
        return statuses;
    }

    public void onTransfer(@ObservesAsync VauSessionReload sessionReload) {
        String backend = sessionReload.getBackend();
        log.info(String.format("[%s] Vau session reload is submitted", backend));
        try {
            Retrier.callAndRetry(
                List.of(1000),
                15000,
                () -> reload(Set.of(backend)),
                statusList -> !statusList.getFirst().contains("try later")
            );
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Error while reloading Vau NP for '%s'", backend), e);
        }
    }

    public List<String> reload(Set<String> targetEpaSet) throws Exception {
        if (semaphore.tryAcquire()) {
            try {
                ConcurrentHashMap<String, EpaAPI> epaBackendMap = epaMultiService.getEpaBackendMap();
                List<Future<VauNpInfo>> futures = new ArrayList<>();
                getUniqueKonnektorsConfigs().forEach((konnektor, config) ->
                    epaBackendMap.entrySet().stream()
                        .filter(e -> targetEpaSet.isEmpty() || targetEpaSet.stream().anyMatch(t -> e.getKey().startsWith(t)))
                        .forEach(e ->
                            futures.add(scheduledThreadPool.submit(() -> getVauNp(konnektor, config, e.getValue())))
                        )
                );
                return collectResults(futures);
            } finally {
                semaphore.release();
            }
        } else {
            return List.of("Reload is in progress, try later");
        }
    }

    private VauNpInfo getVauNp(String konnektor, KonnektorConfig config, EpaAPI api) {
        String backend = api.getBackend();

        long start = System.currentTimeMillis();
        try {
            RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, config.getUserConfigurations());
            String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
            AuthorizationSmcBApi authorizationSmcBApi = api.getAuthorizationSmcBApi();
            VauNpKey key = new VauNpKey(smcbHandle, konnektor, backend);
            String vauNp = idpClient.getVauNpSync(authorizationSmcBApi, runtimeConfig, smcbHandle, backend);
            long delta = System.currentTimeMillis() - start;

            String okStatus = String.format(STATUS_TEMPLATE, backend, delta, "OK");
            api.getVauFacade().setVauNpSessionStatus(okStatus, true);
            return new VauNpInfo(key, vauNp, okStatus);
        } catch (Exception e) {
            String msg = String.format("Error while getVauNpSync konnektor=%s, ePA=%s", konnektor, backend);
            log.log(Level.SEVERE, msg, e);
            long delta = System.currentTimeMillis() - start;
            String errorStatus = String.format(STATUS_TEMPLATE, backend, delta, e.getMessage());
            api.getVauFacade().setVauNpSessionStatus(errorStatus, false);
            return new VauNpInfo(null, null, errorStatus);
        }
    }

    public String getVauNp(String smcbHandle, String konnektorBaseUrl, String epaBackend) {
        URI uri = URI.create(konnektorBaseUrl);
        return vauNpMap.get(new VauNpKey(smcbHandle, uri.getHost(), epaBackend));
    }
}
