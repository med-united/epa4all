package de.servicehealth.epa4all.server.xdsdocument;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.xds.ProvideAndRegisterSingleDocumentTypeBuilder;
import de.servicehealth.epa4all.xds.author.AuthorPerson;
import de.servicehealth.epa4all.xds.ebrim.FolderDefinition;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import de.servicehealth.epa4all.xds.structure.StructureDefinitionService;
import de.servicehealth.model.EntitlementRequestType;
import de.servicehealth.model.ValidToResponseType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType.Document;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.cxf.client.ClientFactory.USER_AGENT;

@RequestScoped
public class XDSDocumentService {

    private static final Logger log = Logger.getLogger(XDSDocumentService.class.getName());

    private final IdpClient idpClient;
    private final MultiEpaService multiEpaService;
    private final InsuranceDataService insuranceDataService;
    private final StructureDefinitionService structureDefinitionService;
    private final ProvideAndRegisterSingleDocumentTypeBuilder provideAndRegisterDocumentBuilder;

    @Inject
    public XDSDocumentService(
        IdpClient idpClient,
        MultiEpaService multiEpaService,
        InsuranceDataService insuranceDataService,
        StructureDefinitionService structureDefinitionService,
        ProvideAndRegisterSingleDocumentTypeBuilder provideAndRegisterDocumentBuilder
    ) {
        this.idpClient = idpClient;
        this.multiEpaService = multiEpaService;
        this.insuranceDataService = insuranceDataService;
        this.structureDefinitionService = structureDefinitionService;
        this.provideAndRegisterDocumentBuilder = provideAndRegisterDocumentBuilder;
    }

    public Pair<EpaAPI, InsuranceData> getEpaInsurantPair(
        String telematikId,
        String kvnr,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        String correlationId = UUID.randomUUID().toString();

        String egkHandle = insuranceDataService.getEGKHandle(runtimeConfig, kvnr);
        InsuranceData insuranceData = insuranceDataService.getInsuranceData(
            telematikId, kvnr, correlationId, egkHandle, smcbHandle, runtimeConfig
        );
        return Pair.of(getEpaAPI(runtimeConfig, insuranceData), insuranceData);
    }

    public RegistryResponseType uploadXDSDocument(
        String telematikId,
        String kvnr,
        String smcbHandle,
        String contentType,
        String languageCode,
        UserRuntimeConfig runtimeConfig,
        byte[] documentBytes
    ) throws Exception {

        Pair<EpaAPI, InsuranceData> epaInsurantPair = getEpaInsurantPair(telematikId, kvnr, smcbHandle, runtimeConfig);
        EpaAPI epaAPI = epaInsurantPair.getLeft();
        InsuranceData insuranceData = epaInsurantPair.getRight();

        UCPersoenlicheVersichertendatenXML versichertendaten = insuranceData.getPersoenlicheVersichertendaten();
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person = versichertendaten.getVersicherter().getPerson();
        String firstName = person.getVorname();
        String lastName = person.getNachname();
        String title = person.getTitel();

        ProvideAndRegisterDocumentSetRequestType request = prepareDocumentSetRequest(
            documentBytes, telematikId, kvnr, contentType, languageCode, firstName, lastName, title
        );
        IDocumentManagementPortType documentManagementPortType = epaAPI.getDocumentManagementPortType();
        return documentManagementPortType.documentRepositoryProvideAndRegisterDocumentSetB(request);
    }

    private ProvideAndRegisterDocumentSetRequestType prepareDocumentSetRequest(
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

    public EpaAPI getEpaAPI(
        UserRuntimeConfig userRuntimeConfig,
        InsuranceData insuranceData
    ) throws Exception {
        String pz = insuranceData.getPz();
        String xInsurantid = insuranceData.getXInsurantId();
        multiEpaService.setXInsurantId(xInsurantid); // TODO refactor
        EpaAPI epaAPI = multiEpaService.getEpaAPI();
        epaAPI.setNp(idpClient.getVauNpSync(userRuntimeConfig));
        setEntitlement(userRuntimeConfig, xInsurantid, pz, epaAPI);
        return epaAPI;
    }

    private void setEntitlement(
        UserRuntimeConfig userRuntimeConfig,
        String xInsurantid,
        String pz,
        EpaAPI epaAPI
    ) throws Exception {
        EntitlementRequestType entitlementRequest = new EntitlementRequestType();
        String entitlementPSJWT = idpClient.createEntitlementPSJWT(pz, userRuntimeConfig);
        entitlementRequest.setJwt(entitlementPSJWT);
        ValidToResponseType response = epaAPI.getEntitlementsApi().setEntitlementPs(
            xInsurantid, USER_AGENT, entitlementRequest
        );
        log.info(response.toString());
    }
}
