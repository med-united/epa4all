package de.servicehealth.epa4all.server.idp.vaunp;

import com.fasterxml.jackson.databind.JsonNode;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.KonnektorsConfigs;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.registry.BeanRegistry;
import de.servicehealth.startup.StartableService;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static de.servicehealth.epa4all.server.cetp.mapper.Utils.getPayloads;
import static de.servicehealth.utils.ServerUtils.createArrayNode;
import static de.servicehealth.utils.ServerUtils.createObjectNode;
import static de.servicehealth.vau.VauClient.VAU_CLIENT;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;
import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;
import static java.util.concurrent.TimeUnit.SECONDS;

@ApplicationScoped
public class VauSessionsJob extends StartableService {

    private static final Logger log = LoggerFactory.getLogger(VauSessionsJob.class.getName());

    private final BeanRegistry registry;
    private final VauNpProvider vauNpProvider;
    private final EpaMultiService epaMultiService;
    private final IKonnektorClient konnektorClient;
    private final KonnektorDefaultConfig konnektorDefaultConfig;

    private List<String> telematikIds;

    @Inject
    @KonnektorsConfigs
    Map<String, KonnektorConfig> konnektorsConfigs;

    @Inject
    public VauSessionsJob(
        BeanRegistry registry,
        VauNpProvider vauNpProvider,
        EpaMultiService epaMultiService,
        IKonnektorClient konnektorClient,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.registry = registry;
        this.vauNpProvider = vauNpProvider;
        this.epaMultiService = epaMultiService;
        this.konnektorClient = konnektorClient;
        this.konnektorDefaultConfig = konnektorDefaultConfig;
    }

    @Override
    public int getPriority() {
        return VauSessionsJobPriority;
    }

    @Override
    public void onStart() throws Exception {
        telematikIds = getTelematikIds();
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

    @Scheduled(
        every = "${epa.vau.sessions.repair.interval.sec:60s}",
        delay = 60,
        delayUnit = SECONDS,
        concurrentExecution = SKIP
    )
    void repairEmptyVauSessionsJob() {
        try {
            vauNpProvider.reload(Set.of());
        } catch (Throwable e) {
            log.error("Error while initializeSessions", e);
        }
    }

    @Scheduled(
        every = "${epa.vau.sessions.refresh.interval.sec:900s}",
        delay = 900,
        delayUnit = SECONDS,
        concurrentExecution = SKIP
    )
    void refreshActiveVauSessionsJob() {
        try {
            List<JsonNode> nodes = refreshActiveVauSessions(null);
            List<String> failedVauClients = getFailedVauClientUuids(nodes, telematikIds);
            registry.getInstances(VauFacade.class).forEach(f ->
                failedVauClients.forEach(uuid -> {
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
}