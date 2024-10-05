package de.servicehealth.epa4all.cxf.interceptor;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;

public class CxfHeadersInterceptor extends AbstractPhaseInterceptor<Message> {

    public CxfHeadersInterceptor() {
        super(Phase.SETUP);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleMessage(Message message) throws Fault {
        MetadataMap<String, String> headers = (MetadataMap<String, String>) message.get(PROTOCOL_HEADERS);
        headers.putSingle(CONTENT_TYPE, APPLICATION_JSON);
    }
}
