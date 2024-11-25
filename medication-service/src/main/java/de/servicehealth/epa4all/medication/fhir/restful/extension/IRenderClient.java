package de.servicehealth.epa4all.medication.fhir.restful.extension;

import java.io.File;

public interface IRenderClient {

    File getPdfFile() throws Exception;

    byte[] getPdfBytes() throws Exception;

    byte[] getXhtmlDocument() throws Exception;
}