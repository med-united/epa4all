package de.servicehealth.epa4all.medication.fhir.restful.extension;

import java.io.File;

public interface IRenderClient {

    byte[] getPdfBytes(String xInsurantid, String xUseragent) throws Exception;

    File getPdfFile(String xInsurantid, String xUseragent) throws Exception;

    String getXhtmlDocument(String xInsurantid, String xUseragent) throws Exception;
}