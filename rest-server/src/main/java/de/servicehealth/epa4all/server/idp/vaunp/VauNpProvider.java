package de.servicehealth.epa4all.server.idp.vaunp;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.KonnektorsConfigs;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.cetp.domain.fault.CetpFault;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.startup.StartableService;
import jakarta.enterprise.context.ApplicationScoped;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@SuppressWarnings("CdiInjectionPointsInspection")
@ApplicationScoped
public class VauNpProvider extends StartableService {

    private static final Logger log = Logger.getLogger(VauNpProvider.class.getName());

    private final Map<VauNpKey, String> vauNpMap = new HashMap<>();

    @Inject
    @Setter
    ManagedExecutor scheduledThreadPool;

    IdpClient idpClient;
    MultiEpaService multiEpaService;
    IKonnektorClient konnektorClient;
    KonnektorDefaultConfig konnektorDefaultConfig;

    @Setter
    @Inject
    @KonnektorsConfigs
    Map<String, KonnektorConfig> konnektorsConfigs;


    @Inject
    public VauNpProvider(
        IdpClient idpClient,
        MultiEpaService multiEpaService,
        IKonnektorClient konnektorClient,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.idpClient = idpClient;
        this.multiEpaService = multiEpaService;
        this.konnektorClient = konnektorClient;
        this.konnektorDefaultConfig = konnektorDefaultConfig;
    }

    @Override
    public int getPriority() {
        return VauNpProviderPriority;
    }

    public void onStart() throws Exception {
        reload(false);
    }

    private Map<String, KonnektorConfig> getUniqueKonnektorsConfigs() {
        // removing ports
        return konnektorsConfigs.entrySet()
            .stream()
            .map(e -> Pair.of(e.getKey().split("_")[1].split(":")[0], e.getValue()))
            .distinct()
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    private Map<VauNpKey, String> loadVauNps(boolean cleanup) {
        try {
            VauNpFile vauNpFile = new VauNpFile(configDirectory);
            if (cleanup) {
                vauNpFile.cleanUp();
            }
            return vauNpFile.get();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error while loading VauNpFile", e);
            return new HashMap<>();
        }
    }

    public void reload(boolean cleanup) throws Exception {
        Map<VauNpKey, String> savedVauNpMap = loadVauNps(cleanup);
        Map<String, KonnektorConfig> uniqueKonnektorsConfigs = getUniqueKonnektorsConfigs();
        ConcurrentHashMap<String, EpaAPI> epaBackendMap = multiEpaService.getEpaBackendMap();
        if (!savedVauNpMap.isEmpty() && sameConfigs(uniqueKonnektorsConfigs, savedVauNpMap, epaBackendMap)) {
            log.info("Using saved NP");
            vauNpMap.putAll(savedVauNpMap);
        } else {
            List<Future<VauNpInfo>> futures = new ArrayList<>();
            uniqueKonnektorsConfigs.forEach((konnektor, config) ->
                epaBackendMap.forEach((backend, api) ->
                    futures.add(scheduledThreadPool.submit(() -> getVauNp(config, api, konnektor)))
                )
            );
            String status = collectResults(futures);
            log.info(String.format("VAU sessions status:\n%s", status));

            new VauNpFile(configDirectory).store(vauNpMap);
        }
    }

    private boolean sameConfigs(
        Map<String, KonnektorConfig> uniqueKonnektorsConfigs,
        Map<VauNpKey, String> savedVauNpMap,
        ConcurrentHashMap<String, EpaAPI> epaBackendMap
    ) {
        for (Map.Entry<String, KonnektorConfig> entry : uniqueKonnektorsConfigs.entrySet()) {
            for (String backend : epaBackendMap.keySet()) {
                try {
                    RuntimeConfig runtimeConfig = new RuntimeConfig(
                        konnektorDefaultConfig, entry.getValue().getUserConfigurations()
                    );
                    String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
                    VauNpKey vauNpKey = new VauNpKey(smcbHandle, entry.getKey(), backend);
                    if (!savedVauNpMap.containsKey(vauNpKey)) {
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

    private String collectResults(List<Future<VauNpInfo>> futures) {
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
        return String.join("\n", statuses);
    }

    private VauNpInfo getVauNp(KonnektorConfig config, EpaAPI api, String konnektor) {
        String backend = api.getBackend();
        String status = "[%s] Took %d ms -> %s";

        long start = System.currentTimeMillis();
        try {
            RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, config.getUserConfigurations());
            String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
            AuthorizationSmcBApi authorizationSmcBApi = api.getAuthorizationSmcBApi();
            VauNpKey key = new VauNpKey(smcbHandle, konnektor, backend);
            String vauNp = idpClient.getVauNpSync(authorizationSmcBApi, runtimeConfig, smcbHandle, backend);
            long delta = System.currentTimeMillis() - start;
            
            String okStatus = String.format(status, backend, delta, "OK");
            return new VauNpInfo(key, vauNp, okStatus);
        } catch (Exception e) {
            long delta = System.currentTimeMillis() - start;
            String errorStatus = String.format(status, backend, delta, e.getMessage());
            return new VauNpInfo(null, null, errorStatus);
        }
    }

    public String getVauNp(String smcbHandle, String konnektorBaseUrl, String epaBackend) {
        URI uri = URI.create(konnektorBaseUrl);
        return vauNpMap.get(new VauNpKey(smcbHandle, uri.getHost(), epaBackend));
    }
}
