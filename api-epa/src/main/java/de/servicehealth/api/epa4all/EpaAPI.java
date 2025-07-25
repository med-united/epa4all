package de.servicehealth.api.epa4all;

import de.servicehealth.api.ConsentDecisionsApi;
import de.servicehealth.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.api.epa4all.entitlement.EntitlementsApi;
import de.servicehealth.api.epa4all.proxy.IAdminProxy;
import de.servicehealth.api.epa4all.proxy.IFhirProxy;
import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.vau.VauFacade;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;

import java.util.Map;

public interface EpaAPI {

    String getBackend();

    VauFacade getVauFacade();

    IDocumentManagementInsurantPortType getDocumentManagementInsurantPortType();

    IDocumentManagementPortType getDocumentManagementPortType(String taskId, Map<String, String> xHeaders);

    AccountInformationApi getAccountInformationApi();

    ConsentDecisionsApi getContentDecisionApi();

    AuthorizationSmcBApi getAuthorizationSmcBApi();

    EntitlementsApi getEntitlementsApi();

    IAdminProxy getAdminProxy();

    IFhirProxy getFhirProxy();
}