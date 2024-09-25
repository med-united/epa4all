package de.servicehealth.epa4all.cxf.transport;

import jakarta.ws.rs.core.HttpHeaders;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.Address;
import org.apache.cxf.transport.http.HttpClientHTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

import java.io.IOException;
import java.net.URI;

import static de.servicehealth.epa4all.cxf.interceptor.CXFVAUInterceptor.VAU_CID;
import static de.servicehealth.epa4all.cxf.interceptor.CXFVAUInterceptor.VAU_DEBUG_SK1_C2S;
import static de.servicehealth.epa4all.cxf.interceptor.CXFVAUInterceptor.VAU_DEBUG_SK1_S2C;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

public class HTTPClientVAUConduit extends HttpClientHTTPConduit {

    public HTTPClientVAUConduit(Bus b, EndpointInfo ei, EndpointReferenceType t) throws IOException {
        super(b, ei, t);
    }

    @Override
    protected void setupConnection(Message message, Address address, HTTPClientPolicy csPolicy) throws IOException {
        String vauCid = (String) message.get(VAU_CID);
        String s2c = (String) message.get(VAU_DEBUG_SK1_S2C);
        String c2s = (String) message.get(VAU_DEBUG_SK1_C2S);

        String str = address.getString().replace("+vau", "");
        URI uri = URI.create(str);
        String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
        String vauUri = uri.getScheme() + "://" + uri.getHost() + port + vauCid;
        Address a = new Address(vauUri, URI.create(vauUri));

        message.put(HttpHeaders.ACCEPT, APPLICATION_OCTET_STREAM);
        message.put(HttpHeaders.CONTENT_TYPE, APPLICATION_OCTET_STREAM);
        message.put("org.apache.cxf.request.uri", vauUri);
        message.put("org.apache.cxf.message.Message.BASE_PATH", vauUri);

        MetadataMap<String, String> headers = (MetadataMap<String, String>) message.get(Message.PROTOCOL_HEADERS);
        headers.putSingle(HttpHeaders.ACCEPT, APPLICATION_OCTET_STREAM);
        headers.putSingle(HttpHeaders.CONTENT_TYPE, APPLICATION_OCTET_STREAM);

        super.setupConnection(message, a, csPolicy);
    }
}
