package de.servicehealth.epa4all.server.idp.vaunp;

import com.fasterxml.jackson.databind.JsonNode;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.KonnektorsConfigs;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.EpaMultiService;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.IdpConfig;
import de.servicehealth.logging.LogField;
import de.servicehealth.model.SendAuthCodeSC200Response;
import de.servicehealth.model.SendAuthCodeSCtype;
import de.servicehealth.startup.StartableService;
import de.servicehealth.vau.Konnektors;
import de.servicehealth.vau.ReloadEmptySessions;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.Setter;
import org.apache.cxf.interceptor.Fault;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static de.health.service.cetp.konnektorconfig.FSConfigService.CONFIG_DELIMETER;
import static de.servicehealth.logging.LogContext.voidMdc;
import static de.servicehealth.logging.LogField.BACKEND;
import static de.servicehealth.logging.LogField.CLIENT_UUID;
import static de.servicehealth.logging.LogField.KONNEKTOR;
import static de.servicehealth.logging.LogField.SMCB_HANDLE;
import static de.servicehealth.logging.LogField.WORKPLACE;
import static de.servicehealth.utils.ServerUtils.createObjectNode;
import static de.servicehealth.utils.ServerUtils.extractJsonNode;
import static java.util.concurrent.TimeUnit.SECONDS;

@SuppressWarnings("CdiInjectionPointsInspection")
@ApplicationScoped
public class VauNpProvider extends StartableService {

    private static final Logger log = LoggerFactory.getLogger(VauNpProvider.class.getName());

    private final Map<String, Semaphore> reloadMap = new HashMap<>();

    @Inject
    @Setter
    ManagedExecutor scheduledThreadPool;

    VauConfig vauConfig;
    IdpConfig idpConfig;
    IdpClient idpClient;
    EpaCallGuard epaCallGuard;
    EpaMultiService epaMultiService;
    IKonnektorClient konnektorClient;
    Instance<VauHandshake> vauHandshake;
    KonnektorDefaultConfig konnektorDefaultConfig;

    @Setter
    @Inject
    @KonnektorsConfigs
    Map<String, KonnektorConfig> konnektorsConfigs;

    @Inject
    public VauNpProvider(
        VauConfig vauConfig,
        IdpConfig idpConfig,
        IdpClient idpClient,
        EpaCallGuard epaCallGuard,
        EpaMultiService epaMultiService,
        IKonnektorClient konnektorClient,
        Instance<VauHandshake> vauHandshake,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.vauConfig = vauConfig;
        this.idpConfig = idpConfig;
        this.idpClient = idpClient;
        this.epaCallGuard = epaCallGuard;
        this.vauHandshake = vauHandshake;
        this.epaMultiService = epaMultiService;
        this.konnektorClient = konnektorClient;
        this.konnektorDefaultConfig = konnektorDefaultConfig;

        epaMultiService.getEpaConfig().getEpaBackends().forEach(backend ->
            reloadMap.put(backend, new Semaphore(1))
        );
    }

    @Produces
    @Konnektors
    public Set<String> konnektors() {
        return konnektorsConfigs.keySet();
    }

    @Override
    public int getPriority() {
        return VauNpProviderPriority;
    }

    @Override
    public void onStart() throws Exception {
        reload(Set.of());
    }

    public void onSessionReload(@ObservesAsync ReloadEmptySessions sessionReload) {
        String backend = sessionReload.getBackend();
        log.info(String.format("[%s] Vau session reload is submitted", backend));
        try {
            reload(Set.of(backend));
        } catch (Exception e) {
            log.error(String.format("Error while reloading Vau NP for '%s'", backend), e);
        }
    }

    public JsonNode reload(Set<String> backendsToReload) throws Exception {
        List<JsonNode> reloading = new ArrayList<>();

        String clientId = idpConfig.getClientId();
        String userAgent = epaMultiService.getEpaConfig().getEpaUserAgent();
        AtomicBoolean reloadHappened = new AtomicBoolean(false);
        Set<JsonNode> statuses = new HashSet<>();
        epaMultiService.getEpaBackendMap().entrySet().stream()
            .filter(e -> backendsToReload.isEmpty() || backendsToReload.stream().anyMatch(t -> e.getKey().startsWith(t)))
            .forEach(e -> {
                String backend = e.getKey();
                String uri = "https://" + backend;
                if (reloadMap.get(backend).tryAcquire()) {
                    try {
                        EpaAPI epaAPI = e.getValue();
                        VauFacade vauFacade = epaAPI.getVauFacade();
                        epaCallGuard.setBlocked(backend, vauFacade.getSessionClients().isEmpty());

                        long start = System.currentTimeMillis();
                        List<Future<?>> futures = new ArrayList<>();
                        konnektorsConfigs.forEach((konnektorWorkplace, konnektorConfig) -> {
                            try {
                                RuntimeConfig runtimeConfig = new RuntimeConfig(
                                    konnektorDefaultConfig,
                                    konnektorConfig.getUserConfigurations()
                                );
                                AuthorizationSmcBApi smcBApi = epaAPI.getAuthorizationSmcBApi();
                                String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
                                vauFacade.getEmptyClients(konnektorWorkplace).forEach(vauClient ->
                                    futures.add(scheduledThreadPool.submit(() -> {
                                        reloadHappened.set(true);
                                        reloadVauClient(
                                            smcBApi,
                                            runtimeConfig,
                                            konnektorWorkplace,
                                            vauClient,
                                            uri,
                                            backend,
                                            smcbHandle,
                                            userAgent,
                                            clientId
                                        );
                                    }))
                                );
                            } catch (Exception ex) {
                                log.error("Error while getting SMC-B", ex);
                            }
                        });
                        for (Future<?> future : futures) {
                            try {
                                future.get(60, SECONDS);
                            } catch (Exception ex) {
                                log.error("Error while creating Vau Session", ex);
                            }
                        }
                        long delta = System.currentTimeMillis() - start;
                        statuses.add(createObjectNode(
                            Map.of(
                                "backend", vauFacade.getBackend(),
                                "took", delta + " ms",
                                "clients", vauFacade.getStatus()
                            )
                        ));
                    } finally {
                        epaCallGuard.setBlocked(backend, false);
                        reloadMap.get(backend).release();
                    }
                } else {
                    reloading.add(createObjectNode(Map.of(backend, "Reload is in progress, try later")));
                }
            });

        statuses.addAll(reloading);
        JsonNode jsonNode = extractJsonNode(statuses);
        if (reloadHappened.get()) {
            log.info(String.format("VAU sessions reload status:\n%s", jsonNode.toPrettyString()));
        }
        return jsonNode;
    }

    private void reloadVauClient(
        AuthorizationSmcBApi smcBApi,
        UserRuntimeConfig runtimeConfig,
        String konnektorWorkplace,
        VauClient vauClient,
        String uri,
        String backend,
        String smcbHandle,
        String userAgent,
        String clientId
    ) {
        try {
            KonnektorWorkplaceInfo info = getKonnektorWorkplaceInfo(konnektorWorkplace);
            String uuid = vauClient.getUuid();
            Map<LogField, String> mdcMap = Map.of(
                BACKEND, backend,
                SMCB_HANDLE, smcbHandle,
                KONNEKTOR, info.konnektor,
                WORKPLACE, info.workplaceId,
                CLIENT_UUID, uuid
            );
            voidMdc(mdcMap, () -> {
                vauHandshake.get().apply(uri, vauClient);
                // A_24881 - Nonce anfordern für Erstellung "Attestation der Umgebung"
                String nonce = smcBApi.getNonce(clientId, userAgent, backend, uuid).getNonce();
                try (Response response = smcBApi.sendAuthRequest(clientId, userAgent, backend, uuid)) {
                    URI location = response.getLocation();
                    SendAuthCodeSCtype authCode = getAuthCode(runtimeConfig, smcbHandle, nonce, location);
                    applyVauNp(smcBApi, vauClient, clientId, userAgent, backend, authCode);
                }
            });
        } catch (Throwable e) {
            log.error("Error while reloadVauClient", e);
            vauClient.setVauInfo(null);
            vauClient.setVauNp(null);
        }
    }

    private void applyVauNp(
        AuthorizationSmcBApi smcBApi,
        VauClient vauClient,
        String clientId,
        String userAgent,
        String backend,
        SendAuthCodeSCtype authCode
    ) {
        try {
            SendAuthCodeSC200Response sc200Response = smcBApi.sendAuthCodeSC(
                clientId,
                userAgent,
                backend,
                vauClient.getUuid(),
                authCode
            );
            vauClient.setVauNp(sc200Response.getVauNp());
        } catch (Exception e) {
            log.error("Error while sendAuthCodeSC", e);
            throw new Fault(e);
        }
    }

    private SendAuthCodeSCtype getAuthCode(
        UserRuntimeConfig runtimeConfig,
        String smcbHandle,
        String nonce,
        URI location
    ) {
        try {
            SendAuthCodeSCtype authCode = idpClient.getAuthCodeSync(nonce, location, smcbHandle, runtimeConfig);
            log.info("Successfully got authCode from IDP");
            return authCode;
        } catch (Exception e) {
            log.error("Error while getting authCode from IDP", e);
            throw new Fault(e);
        }
    }

    private static class KonnektorWorkplaceInfo {
        String konnektor;
        String workplaceId;

        public KonnektorWorkplaceInfo(String konnektor, String workplaceId) {
            this.konnektor = konnektor;
            this.workplaceId = workplaceId;
        }
    }

    private static KonnektorWorkplaceInfo getKonnektorWorkplaceInfo(String konnektorWorkplace) {
        String konnektor = null;
        String workplaceId = null;
        try {
            String[] parts = konnektorWorkplace.split(CONFIG_DELIMETER);
            konnektor = parts[0].isEmpty() ? null : parts[0];
            workplaceId = parts[1];
        } catch (Exception e) {
            String msg = "KonnektorConfig key is corrupted: '%s', check if property has old format";
            log.warn(String.format(msg, konnektorWorkplace));
        }
        return new KonnektorWorkplaceInfo(konnektor, workplaceId);
    }
}