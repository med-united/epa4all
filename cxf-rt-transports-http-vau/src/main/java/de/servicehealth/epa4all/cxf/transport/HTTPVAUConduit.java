package de.servicehealth.epa4all.cxf.transport;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.cxf.Bus;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.Address;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

public class HTTPVAUConduit extends HTTPConduit {

    public HTTPVAUConduit(Bus b, EndpointInfo ei, EndpointReferenceType t) throws IOException {
        super(b, ei, t);
    }

    @Override
    protected void setupConnection(Message message, Address address, HTTPClientPolicy csPolicy) throws IOException {
        // DO VAU Handshake 
        throw new UnsupportedOperationException("Unimplemented method 'setupConnection'");
    }

    @Override
    protected OutputStream createOutputStream(Message message, boolean needToCacheRequest, boolean isChunking,
            int chunkThreshold) throws IOException {
        // DO VAU encryption Handshake
                throw new UnsupportedOperationException("Unimplemented method 'createOutputStream'");
    }

}
