package de.servicehealth.epa4all.integration.bc.wiremock.setup;

import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class CallInfo {
    List<Pair<String, String>> innerHeaders = new ArrayList<>();
    String errorHeader;
    String contentType;
    byte[] payload;
    int status = 200;

    public CallInfo withInnerHeaders(List<Pair<String, String>> innerHeaders) {
        this.innerHeaders = new ArrayList<>(innerHeaders);
        return this;
    }

    public CallInfo withErrorHeader(String errorHeader) {
        this.errorHeader = errorHeader;
        return this;
    }

    public CallInfo withJsonPayload(byte[] payload) {
        this.payload = payload;
        contentType = "application/json";
        return this;
    }

    public CallInfo withPdfPayload(byte[] payload) {
        this.payload = payload;
        contentType = "application/pdf";
        return this;
    }

    public CallInfo withStatus(int status) {
        this.status = status;
        return this;
    }
}