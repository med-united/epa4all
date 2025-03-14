package de.servicehealth.epa4all.medication.fhir.restful.extension.render;

import java.io.File;
import java.util.Map;

public interface IRenderClient {

    File getPdfFile(String telematikId, Map<String, String> xHeaders) throws Exception;

    byte[] getPdfBytes(Map<String, String> xHeaders) throws Exception;

    byte[] getXhtmlDocument(Map<String, String> xHeaders) throws Exception;
}