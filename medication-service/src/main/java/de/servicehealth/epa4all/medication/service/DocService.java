package de.servicehealth.epa4all.medication.service;

import de.servicehealth.epa4all.medication.fhir.restful.extension.IRenderClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DocService {

    public final static String USER_AGENT = "CLIENTID1234567890AB/2.1.12-45";

    private final IRenderClient renderClient;

    @Inject
    public DocService(IRenderClient renderClient) {
        this.renderClient = renderClient;
    }

    public byte[] getPdfBytes(String xInsurantid) throws Exception {
        return renderClient.getPdfBytes(xInsurantid, USER_AGENT);
    }
}
