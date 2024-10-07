package de.servicehealth.epa4all.cxf.transport;

import de.servicehealth.epa4all.cxf.interceptor.EmptyBody;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.Address;
import org.apache.cxf.transport.http.HttpClientHTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;

import static com.google.common.net.HttpHeaders.CONNECTION;
import static com.google.common.net.HttpHeaders.KEEP_ALIVE;
import static de.servicehealth.epa4all.cxf.interceptor.CxfVauWriteInterceptor.VAU_CID;
import static de.servicehealth.epa4all.cxf.interceptor.CxfVauWriteInterceptor.VAU_DEBUG_SK1_C2S;
import static de.servicehealth.epa4all.cxf.interceptor.CxfVauWriteInterceptor.VAU_DEBUG_SK1_S2C;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static org.apache.cxf.message.Message.ENDPOINT_ADDRESS;
import static org.apache.cxf.message.Message.HTTP_REQUEST_METHOD;
import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;
import static org.apache.cxf.transport.http.Headers.EMPTY_REQUEST_PROPERTY;

public class HTTPClientVauConduit extends HttpClientHTTPConduit {

    public static final String VAU_METHOD_PATH = "VAU-METHOD-PATH";

    public HTTPClientVauConduit(Bus b, EndpointInfo ei, EndpointReferenceType t) throws IOException {
        super(b, ei, t);
    }

    @SuppressWarnings("unchecked")
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

        String method = (String) message.get(HTTP_REQUEST_METHOD);
        String endpoint = (String) message.get(ENDPOINT_ADDRESS);
        endpoint = endpoint.replace(vauCid, "").replace("https:", "https+vau:");
        message.put(ENDPOINT_ADDRESS, endpoint);
        String path = URI.create(endpoint).getPath();

        boolean post = method.equals("POST");
        if (!post) {
            message.put(HTTP_REQUEST_METHOD, "POST");
            message.put(EMPTY_REQUEST_PROPERTY, false);
            message.setContent(List.class, new MessageContentsList(new EmptyBody()));
            message.put(Type.class, EmptyBody.class);
            message.put("proxy.method.parameter.body.index", -1);
        }

        message.put(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
        message.put(ACCEPT, APPLICATION_OCTET_STREAM);
        message.put("org.apache.cxf.request.uri", vauUri);

        MetadataMap<String, String> headers = (MetadataMap<String, String>) message.get(PROTOCOL_HEADERS);
        headers.putSingle(CONNECTION, KEEP_ALIVE);
        headers.putSingle(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
        headers.putSingle(ACCEPT, APPLICATION_OCTET_STREAM);
        headers.putSingle(VAU_METHOD_PATH, method + " " + path);

        super.setupConnection(message, a, csPolicy);
    }
}
