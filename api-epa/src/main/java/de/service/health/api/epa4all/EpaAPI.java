package de.service.health.api.epa4all;

import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.api.EntitlementsApi;
import de.servicehealth.epa4all.medication.fhir.restful.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IRenderClient;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;

public interface EpaAPI {

    String getBackend();

    IDocumentManagementInsurantPortType getDocumentManagementInsurantPortType();

    IDocumentManagementPortType getDocumentManagementPortType();

    AccountInformationApi getAccountInformationApi();

    AuthorizationSmcBApi getAuthorizationSmcBApi();

    IMedicationClient getMedicationClient();

    EntitlementsApi getEntitlementsApi();

    IRenderClient getRenderClient();

    void setNp(String np);

    void setXInsurantid(String insurantId);

    String getNp();

    String getXInsurantid();
}
