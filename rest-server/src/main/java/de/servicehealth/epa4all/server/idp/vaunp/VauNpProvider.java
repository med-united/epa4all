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
import de.servicehealth.epa4all.server.idp.IdpConfig;
import de.servicehealth.model.SendAuthCodeSC200Response;
import de.servicehealth.model.SendAuthCodeSCtype;
import de.servicehealth.startup.StartableService;
import de.servicehealth.vau.VauFacade;
import de.servicehealth.vau.VauSessionReload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.Response;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logmanager.Level;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.health.service.cetp.konnektorconfig.FSConfigService.CONFIG_DELIMETER;

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

    IdpConfig idpConfig;
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
        IdpConfig idpConfig,
        IdpClient idpClient,
        EpaCallGuard epaCallGuard,
        EpaMultiService epaMultiService,
        IKonnektorClient konnektorClient,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.idpConfig = idpConfig;
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
        Set<String> statuses;
        Map<VauNpKey, String> cachedNps = new VauNpFile(configDirectory).get();
        if (!cachedNps.isEmpty() && sameConfigs(cachedNps)) {
            vauNpMap.putAll(cachedNps);
            statuses = cachedNps.keySet().stream()
                .map(s -> String.format(STATUS_TEMPLATE, s.getEpaBackend(), 0, "Restored from cache"))
                .collect(Collectors.toSet());
        } else {
            statuses = reload(Set.of());
        }
        log.info(String.format("VAU sessions status:\n%s", String.join("\n", statuses)));
    }

    private boolean sameConfigs(Map<VauNpKey, String> cachedVauNps) {
        for (Map.Entry<String, KonnektorConfig> entry : konnektorsConfigs.entrySet()) {
            for (String backend : epaMultiService.getEpaBackendMap().keySet()) {
                String konnektorWorkplace = entry.getKey();
                KonnektorConfig konnektorConfig = entry.getValue();
                try {
                    IUserConfigurations userConfigurations = konnektorConfig.getUserConfigurations();
                    RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, userConfigurations);
                    String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
                    KonnektorWorkplaceInfo info = getKonnektorWorkplaceInfo(konnektorWorkplace);
                    VauNpKey vauNpKey = new VauNpKey(smcbHandle, info.konnektor, info.workplaceId, backend);
                    if (!cachedVauNps.containsKey(vauNpKey)) {
                        return false;
                    }
                } catch (CetpFault e) {
                    String msg = String.format("Error while getting SMC-B handle: Konnektor=%s", konnektorWorkplace);
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

    private Set<String> collectResults(List<VauNpInfo> vauNpInfos) throws Exception {
        Set<String> statuses = new HashSet<>();
        vauNpInfos.forEach(vauInfo -> {
            try {
                if (vauInfo.hasValue()) {
                    vauNpMap.put(vauInfo.key, vauInfo.vauNp);
                }
                statuses.add(vauInfo.status);
            } catch (Exception e) {
                statuses.add(e.getMessage());
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

    public Set<String> reload(Set<String> backendsToReload) throws Exception {
        List<String> reloading = new ArrayList<>();

        String clientId = idpConfig.getClientId();
        String userAgent = epaMultiService.getEpaConfig().getEpaUserAgent();

        List<VauNpInfo> vauNpInfos = new ArrayList<>();
        epaMultiService.getEpaBackendMap().entrySet().stream()
            .filter(e -> backendsToReload.isEmpty() || backendsToReload.stream().anyMatch(t -> e.getKey().startsWith(t)))
            .forEach(e -> {
                String backend = e.getKey();
                if (reloadMap.get(backend).tryAcquire()) {
                    epaCallGuard.setBlocked(backend, true);
                    try {
                        EpaAPI epaAPI = e.getValue();
                        VauFacade vauFacade = epaAPI.getVauFacade();
                        AuthorizationSmcBApi smcBApi = epaAPI.getAuthorizationSmcBApi();

                        konnektorsConfigs.entrySet().stream()
                            .map(entry ->
                                scheduledThreadPool.submit(() -> {
                                    // A_24881 - Nonce anfordern für Erstellung "Attestation der Umgebung"
                                    String nonce = smcBApi.getNonce(clientId, userAgent, backend).getNonce();
                                    try (Response response = smcBApi.sendAuthorizationRequestSCWithResponse(clientId, userAgent, backend)) {
                                        URI location = response.getLocation();
                                        log.info(String.format("[%s] nonce=%s query=%s", entry.getKey(), nonce, location.getQuery()));
                                        return getSendAuthCodeSC(entry.getKey(), entry.getValue(), backend, nonce, location);
                                    }
                                })
                            ).map(f -> {
                                try {
                                    return f.get(30, TimeUnit.SECONDS);
                                } catch (Exception ex) {
                                    log.log(Level.SEVERE, "Error while getSendAuthCodeSC", ex);
                                    return null;
                                }
                            }).filter(
                                Objects::nonNull
                            ).collect(
                                Collectors.toMap(Pair::getKey, Pair::getValue)
                            ).forEach((vauNpKey, authCode) -> {
                                VauNpInfo sameKonnektorVauInfo = getVauInfoForSameKonnektor(vauNpInfos, vauNpKey);
                                VauNpInfo vauNpInfo;
                                if (sameKonnektorVauInfo == null) {
                                    vauNpInfo = addVauNpInfo(smcBApi, vauFacade, clientId, userAgent, backend, vauNpKey, authCode);
                                } else {
                                    vauNpInfo = new VauNpInfo(vauNpKey, sameKonnektorVauInfo.vauNp, sameKonnektorVauInfo.status);
                                }
                                vauNpInfos.add(vauNpInfo);
                            });
                    } finally {
                        epaCallGuard.setBlocked(backend, false);
                        reloadMap.get(backend).release();
                    }
                } else {
                    reloading.add(String.format("[%s] Reload is in progress, try later", backend));
                }
            });
        Set<String> statuses = collectResults(vauNpInfos);
        statuses.addAll(reloading);
        return statuses;
    }

    private VauNpInfo getVauInfoForSameKonnektor(List<VauNpInfo> vauNpInfos, VauNpKey vauNpKey) {
        return vauNpInfos.stream()
            .filter(info -> info.key.getKonnektor().equals(vauNpKey.getKonnektor()))
            .filter(info -> info.key.getSmcbHandle().equals(vauNpKey.getSmcbHandle()))
            .filter(info -> info.vauNp != null)
            .findFirst().orElse(null);
    }

    private VauNpInfo addVauNpInfo(
        AuthorizationSmcBApi smcBApi,
        VauFacade vauFacade,
        String clientId,
        String userAgent,
        String backend,
        VauNpKey vauNpKey,
        SendAuthCodeSCtype authCode
    ) {
        long start = System.currentTimeMillis();
        try {
            SendAuthCodeSC200Response sc200Response = smcBApi.sendAuthCodeSC(clientId, userAgent, backend, authCode);
            String vauNp = sc200Response.getVauNp();
            long delta = System.currentTimeMillis() - start;

            String okStatus = String.format(STATUS_TEMPLATE, backend, delta, "OK");
            vauFacade.setVauNpSessionStatus(okStatus);
            return new VauNpInfo(vauNpKey, vauNp, okStatus);
        } catch (Throwable t) {
            String konnektorWorkplace = String.join(CONFIG_DELIMETER, vauNpKey.getKonnektor(), vauNpKey.getWorkplaceId());
            String msg = String.format("Error while sendAuthCodeSC konnektor=%s, ePA=%s", konnektorWorkplace, backend);
            log.log(Level.SEVERE, msg, t);
            long delta = System.currentTimeMillis() - start;
            String errorStatus = String.format(STATUS_TEMPLATE, backend, delta, t.getMessage());
            vauFacade.setVauNpSessionStatus(errorStatus);
            return new VauNpInfo(null, null, errorStatus);
        }
    }

    private Pair<VauNpKey, SendAuthCodeSCtype> getSendAuthCodeSC(
        String konnektorWorkplace,
        KonnektorConfig config,
        String backend,
        String nonce,
        URI location
    ) throws Exception {
        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, config.getUserConfigurations());
        String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
        KonnektorWorkplaceInfo info = getKonnektorWorkplaceInfo(konnektorWorkplace);
        VauNpKey key = new VauNpKey(smcbHandle, info.konnektor, info.workplaceId, backend);

        return Pair.of(key, idpClient.getAuthCodeSync(nonce, location, runtimeConfig, smcbHandle));
    }

    private static class KonnektorWorkplaceInfo {
        String konnektor;
        String workplaceId;

        public KonnektorWorkplaceInfo(String konnektor, String workplaceId) {
            this.konnektor = konnektor;
            this.workplaceId = workplaceId;
        }
    }

    private KonnektorWorkplaceInfo getKonnektorWorkplaceInfo(String konnektorWorkplace) {
        String konnektor = null;
        String workplaceId = null;
        boolean corrupted = konnektorWorkplace.trim().isEmpty();
        if (!corrupted) {
            String[] parts = konnektorWorkplace.split(CONFIG_DELIMETER);
            if (parts.length == 1) {
                konnektor = parts[0];
            } else if (parts.length == 2) {
                konnektor = parts[0];
                workplaceId = parts[1];
            } else {
                corrupted = true;
            }
        }
        if (corrupted) {
            log.warning(String.format("KonnektorConfig key is corrupted: '%s', check if property has old format", konnektorWorkplace));
        }
        return new KonnektorWorkplaceInfo(konnektor, workplaceId);
    }

    public Optional<String> getVauNp(
        @NotNull String smcbHandle,
        @NotNull String konnektorHost,
        @NotNull String workplaceId,
        @NotNull String epaBackend
    ) {
        return Optional.ofNullable(vauNpMap.get(new VauNpKey(smcbHandle, konnektorHost, workplaceId, epaBackend)));
    }
}
