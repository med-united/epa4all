package de.service.health.api.epa4all;

import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.api.EntitlementsApi;
import de.servicehealth.epa4all.medication.fhir.restful.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IRenderClient;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;

public class EpaAPIAggregator implements EpaAPI {

    private final String backend;
    private final IDocumentManagementPortType documentManagementPortType;
    private final IDocumentManagementInsurantPortType documentManagementInsurantPortType;
    private final AccountInformationApi accountInformationApi;
    private final AuthorizationSmcBApi authorizationSmcBApi;
    private final EntitlementsApi entitlementsApi;
    private final IMedicationClient medicationClient;
    private final IRenderClient renderClient;

    public EpaAPIAggregator(
        String backend,
        IDocumentManagementPortType documentManagementPortType,
        IDocumentManagementInsurantPortType documentManagementInsurantPortType,
        AccountInformationApi accountInformationApi,
        AuthorizationSmcBApi authorizationSmcBApi,
        EntitlementsApi entitlementsApi,
        IMedicationClient medicationClient,
        IRenderClient renderClient
    ) {
        this.backend = backend;
        this.documentManagementPortType = documentManagementPortType;
        this.documentManagementInsurantPortType = documentManagementInsurantPortType;
        this.accountInformationApi = accountInformationApi;
        this.authorizationSmcBApi = authorizationSmcBApi;
        this.entitlementsApi = entitlementsApi;
        this.medicationClient = medicationClient;
        this.renderClient = renderClient;
    }

    @Override
    public String getBackend() {
        return backend;
    }

    @Override
    public AuthorizationSmcBApi getAuthorizationSmcBApi() {
        return authorizationSmcBApi;
    }

    @Override
    public AccountInformationApi getAccountInformationApi() {
        return accountInformationApi;
    }

    @Override
    public IDocumentManagementInsurantPortType getDocumentManagementInsurantPortType() {
        return documentManagementInsurantPortType;
    }

    @Override
    public IDocumentManagementPortType getDocumentManagementPortType() {
        return documentManagementPortType;
    }

    @Override
    public IMedicationClient getMedicationClient() {
        return medicationClient;
    }

    @Override
    public EntitlementsApi getEntitlementsApi() {
        return entitlementsApi;
    }

    @Override
    public IRenderClient getRenderClient() {
        return renderClient;
    }
}
