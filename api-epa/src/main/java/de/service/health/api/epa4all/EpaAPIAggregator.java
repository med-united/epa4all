package de.service.health.api.epa4all;

import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.service.health.api.epa4all.entitlement.EntitlementsApi;
import de.service.health.api.epa4all.proxy.IFhirProxy;
import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.IRenderClient;
import de.servicehealth.vau.VauFacade;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;

import java.util.function.Supplier;

public class EpaAPIAggregator implements EpaAPI {

    private final String backend;
    private final VauFacade vauFacade;
    private final IRenderClient renderClient;
    private final IMedicationClient medicationClient;
    private final Supplier<IDocumentManagementPortType> documentManagementPortType;
    private final IDocumentManagementInsurantPortType documentManagementInsurantPortType;
    private final AccountInformationApi accountInformationApi;
    private final AuthorizationSmcBApi authorizationSmcBApi;
    private final EntitlementsApi entitlementsApi;
    private final IFhirProxy fhirProxy;

    public EpaAPIAggregator(
        String backend,
        VauFacade vauFacade,
        IRenderClient renderClient,
        IMedicationClient medicationClient,
        Supplier<IDocumentManagementPortType> documentManagementPortType,
        IDocumentManagementInsurantPortType documentManagementInsurantPortType,
        AccountInformationApi accountInformationApi,
        AuthorizationSmcBApi authorizationSmcBApi,
        EntitlementsApi entitlementsApi,
        IFhirProxy fhirProxy
    ) {
        this.backend = backend;
        this.vauFacade = vauFacade;
        this.renderClient = renderClient;
        this.medicationClient = medicationClient;
        this.documentManagementPortType = documentManagementPortType;
        this.documentManagementInsurantPortType = documentManagementInsurantPortType;
        this.accountInformationApi = accountInformationApi;
        this.authorizationSmcBApi = authorizationSmcBApi;
        this.entitlementsApi = entitlementsApi;
        this.fhirProxy = fhirProxy;
    }

    @Override
    public String getBackend() {
        return backend;
    }

    @Override
    public VauFacade getVauFacade() {
        return vauFacade;
    }

    @Override
    public IDocumentManagementInsurantPortType getDocumentManagementInsurantPortType() {
        return documentManagementInsurantPortType;
    }

    @Override
    public IDocumentManagementPortType getDocumentManagementPortType() {
        return documentManagementPortType.get();
    }

    @Override
    public AccountInformationApi getAccountInformationApi() {
        return accountInformationApi;
    }

    @Override
    public AuthorizationSmcBApi getAuthorizationSmcBApi() {
        return authorizationSmcBApi;
    }

    @Override
    public EntitlementsApi getEntitlementsApi() {
        return entitlementsApi;
    }

    @Override
    public IFhirProxy getFhirProxy() {
        return fhirProxy;
    }

    @Override
    public IMedicationClient getMedicationClient() {
        return medicationClient;
    }

    @Override
    public IRenderClient getRenderClient() {
        return renderClient;
    }
}
