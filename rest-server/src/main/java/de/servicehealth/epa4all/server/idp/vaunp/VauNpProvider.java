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
import org.jboss.logmanager.Level;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.servicehealth.utils.ServerUtils.terminateExecutor;

@ApplicationScoped
public class VauNpProvider extends StartableService {

    private static final Logger log = Logger.getLogger(VauNpProvider.class.getName());

    private final Map<VauNpKey, String> vauNpMap = new HashMap<>();
    private final ExecutorService scheduledThreadPool = Executors.newFixedThreadPool(5);

    @Inject
    IdpClient idpClient;

    @Inject
    MultiEpaService multiEpaService;

    @Inject
    IKonnektorClient konnektorClient;

    @Inject
    KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    @KonnektorsConfigs
    Map<String, KonnektorConfig> konnektorsConfigs;

    @Setter
    @ConfigProperty(name = "ere.per.konnektor.config.folder")
    String configFolder;

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
        // removing cetp port
        return konnektorsConfigs.entrySet()
            .stream()
            .map(e -> Pair.of(e.getKey().split("_")[1], e.getValue()))
            .distinct()
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    public void onStart() {
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            terminateExecutor(scheduledThreadPool, "Vau-Np-Job", 6000))
        );
        var konnektorConfigFolder = new File(configFolder);
        if (!konnektorConfigFolder.exists() || !konnektorConfigFolder.isDirectory()) {
            throw new IllegalStateException("Konnektor config directory is corrupted");
        }
        try {
            Map<String, KonnektorConfig> uniqueKonnektorsConfigs = getUniqueKonnektorsConfigs();
            VauNpFile vauNpFile = new VauNpFile(konnektorConfigFolder);
            Map<VauNpKey, String> savedVauNpMap = vauNpFile.get();
            ConcurrentHashMap<String, EpaAPI> epaBackendMap = multiEpaService.getEpaBackendMap();
            if (sameConfigs(uniqueKonnektorsConfigs, savedVauNpMap, epaBackendMap)) {
                vauNpMap.putAll(savedVauNpMap);
            } else {
                int k = uniqueKonnektorsConfigs.size();
                int b = epaBackendMap.size();
                log.info(String.format("Gathering VauNP is started for %d konnektors and %d epa-backends", k, b));

                List<Future<Pair<VauNpKey, String>>> futures = new ArrayList<>();
                uniqueKonnektorsConfigs.forEach((konnektor, config) ->
                    epaBackendMap.forEach((backend, api) ->
                        futures.add(scheduledThreadPool.submit(() -> getVauNp(config, api, konnektor, backend)))
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

    private Pair<VauNpKey, String> getVauNp(KonnektorConfig config, EpaAPI api, String konnektor, String backend) {
        try {
            RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, config.getUserConfigurations());
            String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
            AuthorizationSmcBApi authorizationSmcBApi = api.getAuthorizationSmcBApi();
            String vauNp = idpClient.getVauNpSync(authorizationSmcBApi, runtimeConfig, smcbHandle, api.getBackend());

            return Pair.of(new VauNpKey(konnektor, backend), vauNp);
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Unable to getVauNP for %s and %s", konnektor, backend), e);
            String message = e.getMessage();
            return Pair.of(null, message);
        }
    }

    public String getVauNp(String konnektorBaseUrl, String epaBackend) {
        URI uri = URI.create(konnektorBaseUrl);
        String host = uri.getHost();
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();

        return vauNpMap.get(new VauNpKey(host + port, epaBackend));
    }
}
