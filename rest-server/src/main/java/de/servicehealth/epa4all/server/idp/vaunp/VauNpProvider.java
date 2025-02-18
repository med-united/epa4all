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
import de.servicehealth.model.SendAuthCodeSC200Response;
import de.servicehealth.model.SendAuthCodeSCtype;
import de.servicehealth.registry.BeanRegistry;
import de.servicehealth.startup.StartableService;
import de.servicehealth.vau.FixDummySessions;
import de.servicehealth.vau.Konnektors;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.Setter;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static de.health.service.cetp.konnektorconfig.FSConfigService.CONFIG_DELIMETER;
import static de.servicehealth.epa4all.server.cetp.mapper.Utils.getPayloads;
import static de.servicehealth.logging.LogContext.withMdc;
import static de.servicehealth.logging.LogField.KONNEKTOR;
import static de.servicehealth.logging.LogField.WORKPLACE;
import static de.servicehealth.utils.ServerUtils.createArrayNode;
import static de.servicehealth.utils.ServerUtils.createObjectNode;
import static de.servicehealth.utils.ServerUtils.extractJsonNode;
import static de.servicehealth.vau.VauClient.VAU_CLIENT;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;
import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;
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
    BeanRegistry registry;
    EpaCallGuard epaCallGuard;
    EpaMultiService epaMultiService;
    IKonnektorClient konnektorClient;
    Instance<VauHandShake> vauHandShakes;
    KonnektorDefaultConfig konnektorDefaultConfig;

    @Setter
    @Inject
    @KonnektorsConfigs
    Map<String, KonnektorConfig> konnektorsConfigs;

    private List<String> telematikIds;

    @Inject
    public VauNpProvider(
        VauConfig vauConfig,
        IdpConfig idpConfig,
        IdpClient idpClient,
        BeanRegistry registry,
        EpaCallGuard epaCallGuard,
        EpaMultiService epaMultiService,
        IKonnektorClient konnektorClient,
        Instance<VauHandShake> vauHandShakes,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.registry = registry;
        this.vauConfig = vauConfig;
        this.idpConfig = idpConfig;
        this.idpClient = idpClient;
        this.epaCallGuard = epaCallGuard;
        this.vauHandShakes = vauHandShakes;
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

    public void onStart() throws Exception {
        telematikIds = getTelematikIds();
        initializeEmptySessions();
    }

    @Scheduled(
        every = "${epa.vau.sessions.refresh.interval.sec:300s}",
        delay = 300,
        delayUnit = SECONDS,
        concurrentExecution = SKIP
    )
    void refreshActiveVauSessionsJob() {
        try {
            List<JsonNode> nodes = refreshActiveVauSessions(null);
            List<String> failedVauClients = getFailedVauClientUuids(nodes, telematikIds);
            registry.getInstances(VauFacade.class).forEach(f -> failedVauClients.forEach(uuid -> {
                VauClient vauClient = f.get(uuid);
                if (vauClient != null) {
                    vauClient.forceRelease(null);
                }
            }));
            if (!failedVauClients.isEmpty()) {
                log.info(String.format("Vau sessions refresh status: %s", createArrayNode(nodes).toPrettyString()));
            }
        } catch (Throwable e) {
            log.error("Error while refreshActiveVauSessions", e);
        }
    }

    @Scheduled(
        every = "${epa.vau.sessions.repair.interval.sec:60s}",
        delay = 60,
        delayUnit = SECONDS,
        concurrentExecution = SKIP
    )
    void repairEmptyVauSessionsJob() {
        try {
            initializeEmptySessions();
        } catch (Throwable e) {
            log.error("Error while initializeSessions", e);
        }
    }

    private void initializeEmptySessions() throws Exception {
        reload(Set.of());
    }

    private static List<String> getFailedVauClientUuids(List<JsonNode> nodes, List<String> telematikIds) {
        return nodes.stream().filter(node -> {
            JsonNode errorNode = node.get("error");
            if (errorNode != null) {
                return true;
            }
            JsonNode authNode = node.get("User-Authentication");
            if (authNode == null) {
                return true;
            } else {
                String telematikId = authNode.textValue();
                return telematikIds.stream().noneMatch(telematikId::contains);
            }
        }).map(node -> node.get(VAU_CLIENT)).filter(Objects::nonNull).map(JsonNode::textValue).toList();
    }

    private List<String> getTelematikIds() {
        return konnektorsConfigs.values().stream().map(konnektorConfig -> {
            try {
                UserRuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, konnektorConfig.getUserConfigurations());
                String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
                return konnektorClient.getTelematikId(runtimeConfig, smcbHandle);
            } catch (Exception ignored) {
                return null;
            }
        }).filter(Objects::nonNull).toList();
    }

    public List<JsonNode> refreshActiveVauSessions(String targetBackend) {
        List<Response> responses = epaMultiService.getEpaBackendMap().entrySet().stream()
            .filter(e -> targetBackend == null || e.getKey().equalsIgnoreCase(targetBackend))
            .flatMap(e -> {
                EpaAPI epaApi = e.getValue();
                String backend = e.getKey();
                Map<String, String> xHeaders = new HashMap<>();
                xHeaders.put(X_USER_AGENT, epaMultiService.getEpaConfig().getEpaUserAgent());
                xHeaders.put(X_BACKEND, backend);

                VauFacade vauFacade = epaApi.getVauFacade();
                return vauFacade.getSessionClients().stream().map(vc -> {
                    try {
                        return epaApi.getAdminProxy().forwardGet("admin/VAU-Status", vc.getUuid(), xHeaders);
                    } catch (Exception ex) {
                        JsonNode errorNode = createObjectNode(Map.of("error", ex.getMessage(), VAU_CLIENT, vc.getUuid()));
                        return Response.status(Response.Status.CONFLICT).entity(errorNode).build();

                    }
                });
            }).toList();

        return getPayloads(responses);
    }

    public void onSessionReload(@ObservesAsync FixDummySessions sessionReload) {
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
                    EpaAPI epaAPI = e.getValue();
                    VauFacade vauFacade = epaAPI.getVauFacade();
                    epaCallGuard.setBlocked(backend, vauFacade.getSessionClients().isEmpty());
                    try {
                        AuthorizationSmcBApi smcBApi = epaAPI.getAuthorizationSmcBApi();
                        long start = System.currentTimeMillis();
                        List<Future<?>> futures = new ArrayList<>();
                        konnektorsConfigs.forEach((konnektorWorkplace, konnektorConfig) ->
                            vauFacade.getEmptyClients(konnektorWorkplace).forEach(vauClient ->
                                futures.add(scheduledThreadPool.submit(() -> {
                                    reloadHappened.set(true);
                                    vauHandShakes.get().apply(uri, vauClient);
                                    // A_24881 - Nonce anfordern für Erstellung "Attestation der Umgebung"
                                    String nonce = smcBApi.getNonce(clientId, userAgent, backend, vauClient.getUuid()).getNonce();
                                    try (Response response = smcBApi.sendAuthorizationRequestSCWithResponse(clientId, userAgent, backend, vauClient.getUuid())) {
                                        URI location = response.getLocation();
                                        AuthCodeInfo authCodeInfo = getIdpAuthCode(konnektorWorkplace, konnektorConfig, nonce, location);
                                        createVauSession(smcBApi, vauFacade, clientId, userAgent, backend, vauClient.getUuid(), authCodeInfo);
                                    }
                                }))));
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

    private void createVauSession(
        AuthorizationSmcBApi smcBApi,
        VauFacade vauFacade,
        String clientId,
        String userAgent,
        String backend,
        String vauClientUuid,
        AuthCodeInfo authCode
    ) {
        try {
            if (authCode.authCode != null) {
                SendAuthCodeSC200Response sc200Response = smcBApi.sendAuthCodeSC(
                    clientId,
                    userAgent,
                    backend,
                    vauClientUuid,
                    authCode.authCode
                );
                vauFacade.get(vauClientUuid).setVauNp(sc200Response.getVauNp());
            } else {
                log.warn(String.format("VauClient %s: vau-np was not set", vauClientUuid));
            }
        } catch (Throwable t) {
            String msg = String.format("Error while sendAuthCodeSC ePA=%s vauClient=%s", backend, vauClientUuid);
            log.error(msg, t);

            vauFacade.handleVauSessionError(vauClientUuid, true, false);
        }
    }

    private AuthCodeInfo getIdpAuthCode(
        String konnektorWorkplace,
        KonnektorConfig config,
        String nonce,
        URI location
    ) {
        KonnektorWorkplaceInfo info = getKonnektorWorkplaceInfo(konnektorWorkplace);
        return withMdc(Map.of(KONNEKTOR, info.konnektor, WORKPLACE, info.workplaceId), () -> {
            try {
                RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, config.getUserConfigurations());
                String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
                SendAuthCodeSCtype authCode = idpClient.getAuthCodeSync(nonce, location, runtimeConfig, smcbHandle);
                log.info("Successfully got authorization code from IDP");
                return new AuthCodeInfo(authCode, null);
            } catch (Exception e) {
                log.error("Error while getting authorization code from IDP", e);
                StringBuilder sb = new StringBuilder(e.getMessage());
                if (e instanceof RuntimeException runtimeEx) {
                    Throwable cause = runtimeEx.getCause();
                    if (cause != null) {
                        sb.append(": ").append(cause.getMessage());
                    }
                }
                return new AuthCodeInfo(null, sb.toString());
            }
        });
    }

    private static class AuthCodeInfo {
        SendAuthCodeSCtype authCode;
        String error;

        public AuthCodeInfo(
            SendAuthCodeSCtype authCode,
            String error
        ) {
            this.authCode = authCode;
            this.error = error;
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
