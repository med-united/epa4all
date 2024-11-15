package de.servicehealth.epa4all.server.xdsdocument;

import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.api.EntitlementsApi;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.xds.ProvideAndRegisterSingleDocumentTypeBuilder;
import de.servicehealth.epa4all.xds.author.AuthorPerson;
import de.servicehealth.epa4all.xds.ebrim.FolderDefinition;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import de.servicehealth.epa4all.xds.structure.StructureDefinitionService;
import de.servicehealth.model.EntitlementRequestType;
import de.servicehealth.model.ValidToResponseType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType.Document;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.apache.cxf.jaxrs.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.cxf.client.ClientFactory.USER_AGENT;
import static de.servicehealth.vau.VauClient.VAU_NP;

@RequestScoped
public class XDSDocumentService {

    private static final Logger log = Logger.getLogger(XDSDocumentService.class.getName());

    private final IdpClient idpClient;
    private final MultiEpaService multiEpaService;
    private final StructureDefinitionService structureDefinitionService;
    private final ProvideAndRegisterSingleDocumentTypeBuilder provideAndRegisterDocumentBuilder;

    @Inject
    public XDSDocumentService(
        IdpClient idpClient,
        MultiEpaService multiEpaService,
        StructureDefinitionService structureDefinitionService,
        ProvideAndRegisterSingleDocumentTypeBuilder provideAndRegisterDocumentBuilder
    ) {
        this.idpClient = idpClient;
        this.multiEpaService = multiEpaService;
        this.structureDefinitionService = structureDefinitionService;
        this.provideAndRegisterDocumentBuilder = provideAndRegisterDocumentBuilder;
    }

    public ProvideAndRegisterDocumentSetRequestType prepareDocumentSetRequest(
        byte[] documentBytes,
        String telematikId,
        String kvnr,
        String contentType,
        String languageCode,
        String firstName,
        String lastName,
        String title
    ) throws Exception {
        Document document = new Document();
        document.setValue(documentBytes);
        String documentId = "DocumentEntry-0";
        document.setId(documentId);

        StructureDefinition structure = structureDefinitionService.getStructureDefinition(contentType, documentBytes);
        List<FolderDefinition> folderDefinitions = structure.getElements().getFirst().getMetadata();
        AuthorPerson authorPerson = new AuthorPerson("123456667", firstName, lastName, title, "PRA"); // TODO

        provideAndRegisterDocumentBuilder.init(
            document,
            folderDefinitions,
            authorPerson,
            telematikId,
            documentId,
            languageCode,
            contentType,
            kvnr
        );
        return provideAndRegisterDocumentBuilder.build();
    }

    public EpaAPI setEntitlementAndGetEpaAPI(
        UserRuntimeConfig userRuntimeConfig,
        InsuranceData insuranceData,
        String smcbHandle
    ) throws Exception {
        String insurantId = insuranceData.getInsurantId();
        EpaAPI epaAPI = multiEpaService.getEpaAPI(insurantId);
        setEntitlement(userRuntimeConfig, insuranceData, epaAPI, smcbHandle); // TODO decouple, implement entitlement management
        return epaAPI;
    }

    private void setEntitlement(
        UserRuntimeConfig userRuntimeConfig,
        InsuranceData insuranceData,
        EpaAPI epaAPI,
        String smcbHandle
    ) throws Exception {
        String insurantId = insuranceData.getInsurantId();
        String pz = insuranceData.getPz();
        EntitlementRequestType entitlementRequest = new EntitlementRequestType();
        String entitlementPSJWT = idpClient.createEntitlementPSJWT(smcbHandle, insurantId, pz, userRuntimeConfig);
        entitlementRequest.setJwt(entitlementPSJWT);
        EntitlementsApi entitlementsApi = epaAPI.getEntitlementsApi();
        Map<String, String> map = Map.of(VAU_NP, idpClient.getVauNpSync(userRuntimeConfig, insurantId, smcbHandle));
        WebClient.getConfig(entitlementsApi).getRequestContext().putAll(map);
        ValidToResponseType response = entitlementsApi.setEntitlementPs(insurantId, USER_AGENT, entitlementRequest);
        log.info(response.toString());
    }
}
