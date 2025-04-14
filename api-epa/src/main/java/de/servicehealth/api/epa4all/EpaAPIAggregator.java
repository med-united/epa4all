package de.servicehealth.api.epa4all;

import de.servicehealth.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.api.epa4all.entitlement.EntitlementsApi;
import de.servicehealth.api.epa4all.proxy.IAdminProxy;
import de.servicehealth.api.epa4all.proxy.IFhirProxy;
import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.vau.VauFacade;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import jakarta.xml.ws.BindingProvider;

import java.util.Map;
import java.util.function.Supplier;

import static de.servicehealth.vau.VauClient.TASK_ID;

public class EpaAPIAggregator implements EpaAPI {

    private final String backend;
    private final VauFacade vauFacade;
    private final Supplier<IDocumentManagementPortType> documentManagementPortType;
    private final IDocumentManagementInsurantPortType documentManagementInsurantPortType;
    private final AccountInformationApi accountInformationApi;
    private final AuthorizationSmcBApi authorizationSmcBApi;
    private final EntitlementsApi entitlementsApi;
    private final IAdminProxy adminProxy;
    private final IFhirProxy fhirProxy;

    public EpaAPIAggregator(
        String backend,
        VauFacade vauFacade,
        Supplier<IDocumentManagementPortType> documentManagementPortType,
        IDocumentManagementInsurantPortType documentManagementInsurantPortType,
        AccountInformationApi accountInformationApi,
        AuthorizationSmcBApi authorizationSmcBApi,
        EntitlementsApi entitlementsApi,
        IAdminProxy adminProxy,
        IFhirProxy fhirProxy
    ) {
        this.backend = backend;
        this.vauFacade = vauFacade;
        this.documentManagementPortType = documentManagementPortType;
        this.documentManagementInsurantPortType = documentManagementInsurantPortType;
        this.accountInformationApi = accountInformationApi;
        this.authorizationSmcBApi = authorizationSmcBApi;
        this.entitlementsApi = entitlementsApi;
        this.adminProxy = adminProxy;
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
    public IDocumentManagementPortType getDocumentManagementPortType(String taskId, Map<String, String> xHeaders) {
        IDocumentManagementPortType documentPortType = documentManagementPortType.get();
        if (documentPortType instanceof BindingProvider bindingProvider) {
            Map<String, Object> requestContext = bindingProvider.getRequestContext();
            requestContext.putAll(xHeaders);
            if (taskId != null) {
                requestContext.put(TASK_ID, taskId);
            }
        }
        return documentPortType;
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
    public IAdminProxy getAdminProxy() {
        return adminProxy;
    }

    @Override
    public IFhirProxy getFhirProxy() {
        return fhirProxy;
    }
}
