package de.servicehealth.epa4all.medication.fhir.restful.extension;

import java.io.File;
import java.util.Map;

public interface IRenderClient {

    byte[] getPdfBytes(Map<String, Object> runtimeAttributes) throws Exception;

    File getPdfFile(Map<String, Object> runtimeAttributes) throws Exception;

    byte[] getXhtmlDocument(Map<String, Object> runtimeAttributes) throws Exception;
}