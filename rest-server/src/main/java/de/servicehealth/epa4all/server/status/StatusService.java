package de.servicehealth.epa4all.server.status;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.cetp.domain.eventservice.card.CardType;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.IdpConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.health.service.cetp.domain.eventservice.card.CardType.SMC_B;

@ApplicationScoped
public class StatusService {

    private static final Logger log = Logger.getLogger(StatusService.class.getName());

    private final ExecutorService scheduledThreadPool = Executors.newFixedThreadPool(5);

    @Inject
    KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    DefaultUserConfig defaultUserConfig;

    @Inject
    IKonnektorClient konnektorClient;

    @Inject
    IdpClient idpClient;

    @Inject
    IdpConfig idpConfig;
    

    public ServerStatus getStatus() {
        ServerStatus status = new ServerStatus();

        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, defaultUserConfig.getUserConfigurations());
        String connectorBaseURL = runtimeConfig.getConnectorBaseURL();
        List<Future<?>> futures = new ArrayList<>();
        futures.add(scheduledThreadPool.submit(() -> {
            try {
                konnektorClient.getCards(runtimeConfig, SMC_B);
                status.setConnectorReachable(true, connectorBaseURL);
            } catch (Exception ex) {
                status.setConnectorReachable(false, connectorBaseURL);
            }
        }));

        futures.add(scheduledThreadPool.submit(() -> {
            String discoveryUrl = "Not given";
            try {
                discoveryUrl = idpConfig.getDiscoveryDocumentUrl();
                idpClient.retrieveDocument();
                status.setIdpReachable(true, discoveryUrl);
            } catch (Throwable e) {
                status.setIdpReachable(false, discoveryUrl + " Exception: " + e.getMessage());
            }
        }));

        futures.add(scheduledThreadPool.submit(() -> {
            String smcbHandle = null;
            try {
                smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
                status.setSmcbAvailable(true, "Card Handle: " + smcbHandle);
            } catch (Exception e) {
                status.setSmcbAvailable(false, "Exception: " + e.getMessage() + " Cause: " + (e.getCause() != null ? e.getCause().getMessage() : ""));
            }
            // CautReadable
            try {
                konnektorClient.getSmcbX509Certificate(runtimeConfig, smcbHandle);
                status.setCautReadable(true, "");
            } catch (Exception e) {
                status.setCautReadable(false, "Exception: " + e.getMessage() + " Cause: " + (e.getCause() != null ? e.getCause().getMessage() : ""));
            }
        }));

        futures.add(scheduledThreadPool.submit(() -> {
            // EhbaAvailable
            try {
                List<Card> hbaCards = konnektorClient.getCards(runtimeConfig, CardType.HBA);
                status.setEhbaAvailable(true, "Card Handle: " + hbaCards.getFirst().getCardHandle());
            } catch (Exception e) {
                status.setEhbaAvailable(false, "Exception: " + e.getMessage() + " Cause: " + (e.getCause() != null ? e.getCause().getMessage() : ""));
            }
        }));

        for (Future<?> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Error while building status", e);
            }
        }
        return status;
    }
}