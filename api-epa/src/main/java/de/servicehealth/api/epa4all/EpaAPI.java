package de.servicehealth.api.epa4all;

import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.api.ConsentDecisionsApi;
import de.servicehealth.api.epa4all.authorization.AuthorizationSmcbAPI;
import de.servicehealth.api.epa4all.entitlement.EntitlementsAPI;
import de.servicehealth.api.epa4all.entitlement.EntitlementsFdvAPI;
import de.servicehealth.api.epa4all.proxy.IAdminProxy;
import de.servicehealth.api.epa4all.proxy.IFhirProxy;
import de.servicehealth.vau.VauFacade;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;

import java.util.Map;

public interface EpaAPI {

    String getBackend();

    VauFacade getVauFacade();

    IDocumentManagementInsurantPortType getDocumentManagementInsurantPortType();

    IDocumentManagementPortType getDocumentManagementPortType(String taskId, Map<String, String> xHeaders);

    AccountInformationApi getAccountInformationAPI();

    ConsentDecisionsApi getContentDecisionAPI();

    AuthorizationSmcbAPI getAuthorizationSmcbAPI();

    EntitlementsFdvAPI getEntitlementsFdvAPI();

    EntitlementsAPI getEntitlementsAPI();

    IAdminProxy getAdminProxy();

    IFhirProxy getFhirProxy();
}