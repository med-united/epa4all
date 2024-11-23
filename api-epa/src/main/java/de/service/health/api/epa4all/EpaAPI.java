package de.service.health.api.epa4all;

import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.api.EntitlementsApi;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IRenderClient;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;

import java.util.Map;

public interface EpaAPI {

    String getBackend();

    IDocumentManagementInsurantPortType getDocumentManagementInsurantPortType();

    IDocumentManagementPortType getDocumentManagementPortType();

    AccountInformationApi getAccountInformationApi();

    AuthorizationSmcBApi getAuthorizationSmcBApi();

    EntitlementsApi getEntitlementsApi();

    IMedicationClient getMedicationClient(Map<String, Object> runtimeAttributes);

    IRenderClient getRenderClient(Map<String, Object> runtimeAttributes);
}
