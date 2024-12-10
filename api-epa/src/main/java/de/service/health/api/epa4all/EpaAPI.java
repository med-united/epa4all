package de.service.health.api.epa4all;

import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.service.health.api.epa4all.entitlement.EntitlementsApi;
import de.service.health.api.epa4all.proxy.IFhirProxy;
import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.render.IRenderClient;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;

public interface EpaAPI {

    String getBackend();

    IDocumentManagementInsurantPortType getDocumentManagementInsurantPortType();

    IDocumentManagementPortType getDocumentManagementPortType();

    AccountInformationApi getAccountInformationApi();

    AuthorizationSmcBApi getAuthorizationSmcBApi();

    EntitlementsApi getEntitlementsApi();

    IFhirProxy getFhirProxy();

    IMedicationClient getMedicationClient();

    IRenderClient getRenderClient();
}
