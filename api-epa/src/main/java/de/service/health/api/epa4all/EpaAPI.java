package de.service.health.api.epa4all;

import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.api.EntitlementsApi;
import de.servicehealth.epa4all.medication.fhir.restful.IMedicationClient;
import de.servicehealth.epa4all.medication.fhir.restful.extension.IRenderClient;
import ihe.iti.xds_b._2007.IDocumentManagementInsurantPortType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;

public interface EpaAPI {

	public String getBackend();

	public IDocumentManagementInsurantPortType getDocumentManagementInsurantPortType();

	public IDocumentManagementPortType getDocumentManagementPortType();

	public AccountInformationApi getAccountInformationApi();

	public AuthorizationSmcBApi getAuthorizationSmcBApi();

	public IMedicationClient getMedicationClient();

	public EntitlementsApi getEntitlementsApi();

	public IRenderClient getRenderClient();
    
    void setNp(String np);
    void setXInsurantid(String insurantId);
    public String getNp();
    public String getXInsurantid();
}
