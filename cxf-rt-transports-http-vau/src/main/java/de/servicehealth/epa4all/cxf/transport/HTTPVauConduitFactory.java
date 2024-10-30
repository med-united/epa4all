package de.servicehealth.epa4all.cxf.transport;

import org.apache.cxf.Bus;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.HTTPConduitFactory;
import org.apache.cxf.transport.http.HTTPTransportFactory;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

import java.io.IOException;

public class HTTPVauConduitFactory implements HTTPConduitFactory {

    @Override
    public HTTPConduit createConduit(
        HTTPTransportFactory f,
        Bus b,
        EndpointInfo localInfo,
        EndpointReferenceType target
    ) throws IOException {
        return new HTTPClientVauConduit(b, localInfo, target);
    }
}
