package de.servicehealth.epa4all.server.idp.vaunp;

import com.google.common.annotations.VisibleForTesting;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.KonnektorsConfigs;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.cetp.domain.fault.CetpFault;
import de.health.service.config.api.IUserConfigurations;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.EpaMultiService;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private final Map<String, Semaphore> reloadMap = new HashMap<>();
    private final Map<VauNpKey, String> vauNpMap = new HashMap<>();

    @Inject
    @Setter
    ManagedExecutor scheduledThreadPool;

    IdpClient idpClient;
    EpaCallGuard epaCallGuard;
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
        EpaCallGuard epaCallGuard,
        EpaMultiService epaMultiService,
        IKonnektorClient konnektorClient,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.idpClient = idpClient;
        this.epaCallGuard = epaCallGuard;
        this.epaMultiService = epaMultiService;
        this.konnektorClient = konnektorClient;
        this.konnektorDefaultConfig = konnektorDefaultConfig;

        epaMultiService.getEpaConfig().getEpaBackends().forEach(backend ->
            reloadMap.put(backend, new Semaphore(1))
        );
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
            return new VauNpFile(configDirectory).get();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error while loading VauNpFile", e);
            return new HashMap<>();
        }
    }

    private boolean sameConfigs(Map<VauNpKey, String> cachedVauNps) {
        for (Map.Entry<String, KonnektorConfig> entry : getUniqueKonnektorsConfigs().entrySet()) {
            for (String backend : epaMultiService.getEpaBackendMap().keySet()) {
                String konnektor = entry.getKey();
                KonnektorConfig konnektorConfig = entry.getValue();
                try {
                    IUserConfigurations userConfigurations = konnektorConfig.getUserConfigurations();
                    RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, userConfigurations);
                    String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
                    VauNpKey vauNpKey = new VauNpKey(smcbHandle, konnektor, backend);
                    if (!cachedVauNps.containsKey(vauNpKey)) {
                        return false;
                    }
                } catch (CetpFault e) {
                    String msg = String.format("Error while getting SMC-B handle: Konnektor=%s", konnektor);
                    log.log(Level.SEVERE, msg, e);
                    return false;
                }
            }
        }
        return true;
    }

    @VisibleForTesting
    public void invalidate() {
        vauNpMap.clear();
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

    private List<String> collectResults(Map<String, Future<VauNpInfo>> backendToFuture) throws Exception {
        List<String> statuses = new ArrayList<>();
        backendToFuture.forEach((backend, future) -> {
            try {
                VauNpInfo vauInfo = future.get(60, TimeUnit.SECONDS);
                if (vauInfo.hasValue()) {
                    vauNpMap.put(vauInfo.key, vauInfo.vauNp);
                }
                statuses.add(vauInfo.status);
            } catch (Exception e) {
                statuses.add(e.getMessage());
            } finally {
                reloadMap.get(backend).release();
            }
        });
        new VauNpFile(configDirectory).update(vauNpMap);
        return statuses;
    }

    public void onSessionReload(@ObservesAsync VauSessionReload sessionReload) {
        String backend = sessionReload.getBackend();
        log.info(String.format("[%s] Vau session reload is submitted", backend));
        try {
            reload(Set.of(backend));
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Error while reloading Vau NP for '%s'", backend), e);
        }
    }

    public List<String> reload(Set<String> backendsToReload) throws Exception {
        List<String> reloading = new ArrayList<>();
        Map<String, Future<VauNpInfo>> futures = new HashMap<>();
        epaMultiService.getEpaBackendMap().entrySet().stream()
            .filter(e -> backendsToReload.isEmpty() || backendsToReload.stream().anyMatch(t -> e.getKey().startsWith(t)))
            .forEach(e -> {
                String backend = e.getKey();
                if (reloadMap.get(backend).tryAcquire()) {
                    epaCallGuard.setBlocked(backend, true);
                    getUniqueKonnektorsConfigs().forEach((konnektor, config) ->
                        futures.put(backend, scheduledThreadPool.submit(() -> getVauNp(konnektor, config, e.getValue())))
                    );
                } else {
                    reloading.add(String.format("[%s] Reload is in progress, try later", backend));
                }
            });
        List<String> statuses = collectResults(futures);
        statuses.addAll(reloading);
        return statuses;
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
            api.getVauFacade().setVauNpSessionStatus(okStatus);
            return new VauNpInfo(key, vauNp, okStatus);
        } catch (Throwable e) {
            String msg = String.format("Error while getVauNpSync konnektor=%s, ePA=%s", konnektor, backend);
            log.log(Level.SEVERE, msg, e);
            long delta = System.currentTimeMillis() - start;
            String errorStatus = String.format(STATUS_TEMPLATE, backend, delta, e.getMessage());
            api.getVauFacade().setVauNpSessionStatus(errorStatus);
            return new VauNpInfo(null, null, errorStatus);
        } finally {
            epaCallGuard.setBlocked(backend, false);
        }
    }

    public Optional<String> getVauNp(String smcbHandle, String konnektorHost, String epaBackend) {
        return Optional.ofNullable(vauNpMap.get(new VauNpKey(smcbHandle, konnektorHost, epaBackend)));
    }
}
