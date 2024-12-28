package de.servicehealth.epa4all.medication.fhir.restful.extension.render;

import org.apache.commons.lang3.NotImplementedException;

import java.io.File;
import java.util.Map;

public class VauRenderStubClient implements IRenderClient {

    @Override
    public File getPdfFile(Map<String, String> xHeaders) {
        throw new NotImplementedException("[Stub] IRenderClient getPdfFile");
    }

    @Override
    public byte[] getPdfBytes(Map<String, String> xHeaders) {
        throw new NotImplementedException("[Stub] IRenderClient getPdfBytes");
    }

    @Override
    public byte[] getXhtmlDocument(Map<String, String> xHeaders) {
        throw new NotImplementedException("[Stub] IRenderClient getXhtmlDocument");
    }
}
