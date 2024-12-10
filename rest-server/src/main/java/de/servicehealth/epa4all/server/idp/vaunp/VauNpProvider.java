package de.servicehealth.epa4all.server.idp.vaunp;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.KonnektorsConfigs;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logmanager.Level;

import java.io.File;
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

    @Setter
    @ConfigProperty(name = "ere.per.konnektor.config.folder")
    String configFolder;

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

    private boolean sameConfigs(
        Map<String, KonnektorConfig> uniqueKonnektorsConfigs,
        Map<VauNpKey, String> savedVauNpMap,
        ConcurrentHashMap<String, EpaAPI> epaBackendMap
    ) {
        for (String konnektor : uniqueKonnektorsConfigs.keySet()) {
            for (String backend : epaBackendMap.keySet()) {
                VauNpKey vauNpKey = new VauNpKey(konnektor, backend);
                if (!savedVauNpMap.containsKey(vauNpKey)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public int getPriority() {
        return VauNpProviderPriority;
    }

    private Map<String, KonnektorConfig> getUniqueKonnektorsConfigs() {
        // removing ports
        return konnektorsConfigs.entrySet()
            .stream()
            .map(e -> Pair.of(e.getKey().split("_")[1].split(":")[0], e.getValue()))
            .distinct()
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    public void onStart() {
        reload(false);
    }

    public void reload(boolean cleanup) {
        var konnektorConfigFolder = new File(configFolder);
        if (!konnektorConfigFolder.exists() || !konnektorConfigFolder.isDirectory()) {
            throw new IllegalStateException("Konnektor config directory is corrupted");
        }
        try {
            VauNpFile vauNpFile = new VauNpFile(konnektorConfigFolder);
            if (cleanup) {
                vauNpFile.cleanUp();
            }
            Map<String, KonnektorConfig> uniqueKonnektorsConfigs = getUniqueKonnektorsConfigs();
            Map<VauNpKey, String> savedVauNpMap = vauNpFile.get();
            ConcurrentHashMap<String, EpaAPI> epaBackendMap = multiEpaService.getEpaBackendMap();
            if (!savedVauNpMap.isEmpty() && sameConfigs(uniqueKonnektorsConfigs, savedVauNpMap, epaBackendMap)) {
            	log.info("Using saved NP");
                vauNpMap.putAll(savedVauNpMap);
            } else {
                int k = uniqueKonnektorsConfigs.size();
                int b = epaBackendMap.size();
                log.info(String.format("Gathering VauNP is started for %d konnektors and %d epa-backends", k, b));

                List<Future<Pair<VauNpKey, String>>> futures = new ArrayList<>();
                uniqueKonnektorsConfigs.forEach((konnektor, config) ->
                    epaBackendMap.forEach((backend, api) ->
                        futures.add(scheduledThreadPool.submit(() -> getVauNp(config, api, konnektor)))
                    )
                );
                List<String> errorMessages = new ArrayList<>();
                for (Future<Pair<VauNpKey, String>> future : futures) {
                    Pair<VauNpKey, String> pair = future.get(60, TimeUnit.SECONDS);
                    VauNpKey vauNpKey = pair.getKey();
                    if (vauNpKey != null && pair.getValue() != null) {
                        vauNpMap.put(vauNpKey, pair.getValue());
                    } else {
                        errorMessages.add(pair.getValue());
                    }
                }
                String errors = errorMessages.isEmpty() ? "" : errorMessages.stream().collect(Collectors.joining("\n", "-> \n", ""));
                log.info(String.format(
                    "Gathering VauNP is STOPPED, %d vauNp values are collected %s", vauNpMap.size(), errors)
                );
                if (!vauNpMap.isEmpty()) {
                    vauNpFile.store(vauNpMap);
                }
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error while VauNP map initialization", e);
        }
    }

    private Pair<VauNpKey, String> getVauNp(KonnektorConfig config, EpaAPI api, String konnektor) {
        String backend = api.getBackend();
        try {
            RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, config.getUserConfigurations());
            String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
            AuthorizationSmcBApi authorizationSmcBApi = api.getAuthorizationSmcBApi();
            String vauNp = idpClient.getVauNpSync(authorizationSmcBApi, runtimeConfig, smcbHandle, backend);

            return Pair.of(new VauNpKey(konnektor, backend), vauNp);
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Unable to getVauNP for %s and %s", konnektor, backend), e);
            String message = e.getMessage();
            return Pair.of(null, message);
        }
    }

    public String getVauNp(String konnektorBaseUrl, String epaBackend) {
        URI uri = URI.create(konnektorBaseUrl);
        return vauNpMap.get(new VauNpKey(uri.getHost(), epaBackend));
    }
}
