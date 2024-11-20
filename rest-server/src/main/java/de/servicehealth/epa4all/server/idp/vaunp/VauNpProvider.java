package de.servicehealth.epa4all.server.idp.vaunp;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.KonnektorsConfigs;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.serviceport.KServicePortProvider;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
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

@ApplicationScoped
public class VauNpProvider {

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
    KServicePortProvider servicePortProvider;

    @Inject
    KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    @KonnektorsConfigs
    Map<String, KonnektorConfig> konnektorsConfigs;

    @Setter
    @ConfigProperty(name = "ere.per.konnektor.config.folder")
    String configFolder;

    @ConfigProperty(name = "startup-events.disabled", defaultValue = "false")
    boolean startupEventsDisabled;


    // this must be started after MultiEpaService
    void onStart(@Observes @Priority(7200) StartupEvent ev) {
        if (startupEventsDisabled) {
            log.warning(String.format("[%s] STARTUP events are disabled by config property", getClass().getSimpleName()));
            return;
        }
        var konnektorConfigFolder = new File(configFolder);
        if (!konnektorConfigFolder.exists() || !konnektorConfigFolder.isDirectory()) {
            throw new IllegalStateException("Konnektor config directory is corrupted");
        }
        try {
            VauNpFile vauNpFile = new VauNpFile(konnektorConfigFolder);
            Map<VauNpKey, String> savedVauNpMap = vauNpFile.get();
            ConcurrentHashMap<String, EpaAPI> epaBackendMap = multiEpaService.getEpaBackendMap();
            if (sameConfigs(savedVauNpMap, epaBackendMap)) {
                vauNpMap.putAll(savedVauNpMap);
            } else {
                List<Future<Pair<VauNpKey, String>>> futures = new ArrayList<>();

                // removing cetp port
                konnektorsConfigs.entrySet()
                    .stream()
                    .map(e -> Pair.of(e.getKey().split("_")[1], e.getValue()))
                    .distinct()
                    .collect(Collectors.toMap(Pair::getKey, Pair::getValue))
                    .forEach((konnektor, config) ->
                        epaBackendMap.forEach((backend, api) ->
                            futures.add(scheduledThreadPool.submit(() -> getVauNp(config, api, konnektor, backend)))
                        )
                    );
                for (Future<Pair<VauNpKey, String>> future : futures) {
                    Pair<VauNpKey, String> pair = future.get(60, TimeUnit.SECONDS);
                    if (pair != null) {
                        vauNpMap.put(pair.getKey(), pair.getValue());
                    }
                }
                if (!vauNpMap.isEmpty()) {
                    vauNpFile.store(vauNpMap);
                }
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error while VauNP map initialization", e);
        }
    }

    private boolean sameConfigs(
        Map<VauNpKey, String> savedVauNpMap,
        ConcurrentHashMap<String, EpaAPI> epaBackendMap
    ) {
        for (String konnektor : konnektorsConfigs.keySet()) {
            for (String backend : epaBackendMap.keySet()) {
                VauNpKey vauNpKey = new VauNpKey(konnektor, backend);
                if (!savedVauNpMap.containsKey(vauNpKey)) {
                    return false;
                }
            }
        }
        return true;
    }

    private Pair<VauNpKey, String> getVauNp(KonnektorConfig config, EpaAPI api, String konnektor, String backend) {
        try {
            RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, config.getUserConfigurations());
            String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
            String vauNp = idpClient.getVauNpSync(api.getAuthorizationSmcBApi(), runtimeConfig, smcbHandle);

            return Pair.of(new VauNpKey(konnektor, backend), vauNp);
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Unable to getVauNP for %s and %s", konnektor, backend), e);
            return null;
        }
    }

    public String getVauNp(String konnektorBaseUrl, String epaBackend) {
        URI uri = URI.create(konnektorBaseUrl);
        String host = uri.getHost();
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();

        return vauNpMap.get(new VauNpKey(host + port, epaBackend));
    }
}
