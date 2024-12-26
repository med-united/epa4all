package de.servicehealth.epa4all.cxf.model;

public class EmptyRequest implements EpaRequest {

    private static final byte[] body = new byte[0];

    @Override
    public byte[] getBody() {
        return body;
    }
}
