package de.servicehealth.epa4all.cxf.transport;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.Address;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.HttpClientHTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

public class HTTPVAUConduit extends HttpClientHTTPConduit {

    public HTTPVAUConduit(Bus b, EndpointInfo ei, EndpointReferenceType t) throws IOException {
        super(b, ei, t);
    }

    @Override
    protected void setupConnection(Message message, Address address, HTTPClientPolicy csPolicy) throws IOException {
        String str = address.getString().replace("+vau", "");
        Address a = new Address(str, URI.create(str));

        message.put(HttpHeaders.ACCEPT, APPLICATION_OCTET_STREAM);
        message.put(HttpHeaders.CONTENT_TYPE, APPLICATION_OCTET_STREAM);

        MetadataMap<String, String> headers = (MetadataMap<String, String>) message.get(Message.PROTOCOL_HEADERS);
        headers.putSingle(HttpHeaders.ACCEPT, APPLICATION_OCTET_STREAM);
        headers.putSingle(HttpHeaders.CONTENT_TYPE, APPLICATION_OCTET_STREAM);

        super.setupConnection(message, a, csPolicy);
    }

    @Override
    protected OutputStream createOutputStream(
        Message message,
        boolean needToCacheRequest,
        boolean isChunking,
        int chunkThreshold
    ) throws IOException {
        return super.createOutputStream(message, needToCacheRequest, isChunking, chunkThreshold);
    }
}
