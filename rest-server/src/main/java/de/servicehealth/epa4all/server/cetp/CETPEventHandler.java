package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.AbstractCETPEventHandler;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.config.api.IUserConfigurations;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.filetracker.download.FileDownload;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.feature.FeatureConfig;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.jboss.logging.MDC;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.health.service.cetp.utils.Utils.printException;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;

public class CETPEventHandler extends AbstractCETPEventHandler {

    private static final Logger log = Logger.getLogger(CETPEventHandler.class.getName());

    private final InsuranceDataService insuranceDataService;
    private final EpaFileDownloader epaFileDownloader;
    private final IKonnektorClient konnektorClient;
    private final EpaMultiService epaMultiService;
    private final RuntimeConfig runtimeConfig;
    private final VauNpProvider vauNpProvider;
    private final FeatureConfig featureConfig;

    public CETPEventHandler(
        CardlinkClient cardlinkClient,
        InsuranceDataService insuranceDataService,
        EpaFileDownloader epaFileDownloader,
        IKonnektorClient konnektorClient,
        EpaMultiService epaMultiService,
        VauNpProvider vauNpProvider,
        RuntimeConfig runtimeConfig,
        FeatureConfig featureConfig
    ) {
        super(cardlinkClient);

        this.insuranceDataService = insuranceDataService;
        this.epaFileDownloader = epaFileDownloader;
        this.konnektorClient = konnektorClient;
        this.epaMultiService = epaMultiService;
        this.runtimeConfig = runtimeConfig;
        this.vauNpProvider = vauNpProvider;
        this.featureConfig = featureConfig;
    }

    @Override
    protected String getTopicName() {
        return "CARD/INSERTED";
    }

    private void logCardInsertedEvent(Map<String, String> paramsMap, String correlationId) {
        String paramsStr = paramsMap.entrySet().stream()
            .filter(p -> !p.getKey().equals("CardHolderName"))
            .map(p -> String.format("key=%s value=%s", p.getKey(), p.getValue())).collect(Collectors.joining(", "));

        log.fine(String.format("[%s] Card inserted: params: %s", correlationId, paramsStr));
    }

    @Override
    protected void processEvent(IUserConfigurations configurations, Map<String, String> paramsMap) {
        // Keep MDC names in sync with virtual-nfc-cardlink
        String correlationId = UUID.randomUUID().toString();
        MDC.put("requestCorrelationId", correlationId);
        MDC.put("iccsn", paramsMap.getOrDefault("ICCSN", "NoICCSNProvided"));
        MDC.put("ctid", paramsMap.getOrDefault("CtID", "NoCtIDProvided"));
        MDC.put("slot", paramsMap.getOrDefault("SlotID", "NoSlotIDProvided"));

        log.info("%s event received with the following payload: %s".formatted(getTopicName(), paramsMap));

        boolean isEGK = "EGK".equalsIgnoreCase(paramsMap.get("CardType"));
        boolean hasCardHandle = paramsMap.containsKey("CardHandle");
        boolean hasSlotID = paramsMap.containsKey("SlotID");
        boolean hasCtID = paramsMap.containsKey("CtID");
        if (isEGK && hasCardHandle && hasSlotID && hasCtID) {
            logCardInsertedEvent(paramsMap, correlationId);

            String iccsn = paramsMap.get("ICCSN");
            String ctId = paramsMap.get("CtID");
            Integer slotId = Integer.parseInt(paramsMap.get("SlotID"));
            try {
                String egkHandle = paramsMap.get("CardHandle");
                String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);
                String telematikId = konnektorClient.getTelematikId(runtimeConfig, smcbHandle);

                InsuranceData insuranceData = insuranceDataService.getLocalInsuranceData(telematikId, egkHandle, runtimeConfig);
                if (insuranceData == null) {
                    if (featureConfig.isExternalPnwEnabled()) {
                        log.warning(String.format(
                            "PNW is not found for EGK=%s, ReadVSD is disabled, use external PNW call", egkHandle
                        ));
                        return;
                    } else {
                        insuranceData = insuranceDataService.readVsd(telematikId, egkHandle, null, smcbHandle, runtimeConfig);
                    }
                }
                String insurantId = insuranceData.getInsurantId();
                EpaAPI epaAPI = epaMultiService.getEpaAPI(insurantId);

                String vauNp = vauNpProvider.getVauNp(smcbHandle, configurations.getConnectorBaseURL(), epaAPI.getBackend());
                Map<String, String> xHeaders = Map.of(
                    X_INSURANT_ID, insurantId, VAU_NP, vauNp, X_BACKEND, epaAPI.getBackend()
                );
                byte[] bytes = epaAPI.getRenderClient().getPdfBytes(xHeaders);

                EpaContext epaContext = new EpaContext(insuranceData, Map.of());
                handleDownloadResponse(bytes, telematikId, epaContext, insurantId);

                String encodedPdf = Base64.getEncoder().encodeToString(bytes);
                Map<String, Object> payload = Map.of("slotId", slotId, "ctId", ctId, "bundles", "PDF:" + encodedPdf);
                cardlinkClient.sendJson(correlationId, iccsn, "eRezeptBundlesFromAVS", payload);
            } catch (Exception e) {
                log.log(Level.WARNING, String.format("[%s] Could not get medication PDF", correlationId), e);
                String error = printException(e);
                cardlinkClient.sendJson(
                    correlationId,
                    iccsn,
                    "receiveTasklistError",
                    Map.of("slotId", slotId, "cardSessionId", "null", "status", 500, "tistatus", "500", "errormessage", error)
                );
            }
        } else {
            String msgFormat = "Ignored \"CARD/INSERTED\" values=%s";
            log.log(Level.INFO, String.format(msgFormat, paramsMap));
        }
    }

    private void handleDownloadResponse(
        byte[] bytes,
        String telematikId,
        EpaContext epaContext,
        String kvnr
    ) throws Exception {
        String taskId = UUID.randomUUID().toString();
        String fileName = UUID.randomUUID() + ".pdf";
        FileDownload fileDownload = new FileDownload(epaContext, taskId, fileName, telematikId, kvnr, null);

        RetrieveDocumentSetResponseType response = new RetrieveDocumentSetResponseType();
        RegistryResponseType registryResponse = new RegistryResponseType();
        registryResponse.setStatus("Success");
        response.setRegistryResponse(registryResponse);

        RetrieveDocumentSetResponseType.DocumentResponse documentResponse = new RetrieveDocumentSetResponseType.DocumentResponse();
        documentResponse.setDocument(bytes);
        documentResponse.setMimeType("application/pdf");
        documentResponse.setDocumentUniqueId(UUID.randomUUID().toString());

        response.getDocumentResponse().add(documentResponse);

        epaFileDownloader.handleDownloadResponse(taskId, fileDownload, response);
    }
}
