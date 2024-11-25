package de.service.health.api.epa4all;

import ca.uhn.fhir.context.FhirContext;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.service.health.api.epa4all.entitlement.EntitlementsApi;
import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.epa4all.medication.fhir.restful.extension.GenericMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IRenderClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.VauRenderClient;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import org.apache.http.client.fluent.Executor;

import java.util.Map;

public class EpaAPIAggregator implements EpaAPI {

    private final String backend;
    private final FhirContext apiContext;
    private final String medicationApiUrl;
    private final Executor renderExecutor;
    private final String medicationRenderUrl;

    private final IDocumentManagementPortType documentManagementPortType;
    private final IDocumentManagementInsurantPortType documentManagementInsurantPortType;
    private final AccountInformationApi accountInformationApi;
    private final AuthorizationSmcBApi authorizationSmcBApi;
    private final EntitlementsApi entitlementsApi;

    public EpaAPIAggregator(
        String backend,
        FhirContext apiContext,
        String medicationApiUrl,
        Executor renderExecutor,
        String medicationRenderUrl,
        IDocumentManagementPortType documentManagementPortType,
        IDocumentManagementInsurantPortType documentManagementInsurantPortType,
        AccountInformationApi accountInformationApi,
        AuthorizationSmcBApi authorizationSmcBApi,
        EntitlementsApi entitlementsApi
    ) {
        this.backend = backend;
        this.apiContext = apiContext;
        this.medicationApiUrl = medicationApiUrl;
        this.renderExecutor = renderExecutor;
        this.medicationRenderUrl = medicationRenderUrl;

        this.documentManagementPortType = documentManagementPortType;
        this.documentManagementInsurantPortType = documentManagementInsurantPortType;
        this.accountInformationApi = accountInformationApi;
        this.authorizationSmcBApi = authorizationSmcBApi;
        this.entitlementsApi = entitlementsApi;
    }

    @Override
    public String getBackend() {
        return backend;
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
    public IMedicationClient getMedicationClient(Map<String, Object> runtimeAttributes) {
        return new GenericMedicationClient(apiContext, medicationApiUrl, runtimeAttributes);
    }

    @Override
    public IRenderClient getRenderClient(Map<String, Object> runtimeAttributes) {
        return new VauRenderClient(renderExecutor, medicationRenderUrl, runtimeAttributes);
    }
}
