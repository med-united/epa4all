package de.servicehealth.epa4all.cxf.transport;

import de.servicehealth.epa4all.cxf.interceptor.EmptyBody;
import org.apache.cxf.Bus;
import org.apache.cxf.binding.soap.Soap12;
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
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.net.HttpHeaders.CONNECTION;
import static com.google.common.net.HttpHeaders.KEEP_ALIVE;
import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauClient.VAU_NON_PU_TRACING;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static org.apache.cxf.message.Message.ENDPOINT_ADDRESS;
import static org.apache.cxf.message.Message.HTTP_REQUEST_METHOD;
import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;
import static org.apache.cxf.message.Message.REQUEST_URI;
import static org.apache.cxf.transport.http.Headers.EMPTY_REQUEST_PROPERTY;

public class HTTPClientVauConduit extends HttpClientHTTPConduit {

    public static final String VAU_METHOD_PATH = "VAU-METHOD-PATH";

    public static final String POST_METHOD = "POST";

    public HTTPClientVauConduit(Bus b, EndpointInfo ei, EndpointReferenceType t) throws IOException {
        super(b, ei, t);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void setupConnection(Message message, Address address, HTTPClientPolicy csPolicy) throws IOException {
        String vauCid = (String) message.get(VAU_CID);
        String vauNonPUTracing = (String) message.get(VAU_NON_PU_TRACING);

        String str = address.getString().replace("+vau", "");
        URI uri = URI.create(str);
        String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
        String vauUri = uri.getScheme() + "://" + uri.getHost() + port + vauCid;
        Address a = new Address(vauUri, URI.create(vauUri));

        String method = (String) message.get(HTTP_REQUEST_METHOD);
        Object soapVersion = message.get("org.apache.cxf.binding.soap.SoapVersion");
        String endpoint = (String) message.get(ENDPOINT_ADDRESS);
        endpoint = endpoint.replace(vauCid, "").replace("https:", "https+vau:");
        message.put(ENDPOINT_ADDRESS, endpoint);
        String path = URI.create(endpoint).getPath();

        boolean post = POST_METHOD.equals(method);
        if (!post && !(soapVersion instanceof Soap12)) {
            message.put(HTTP_REQUEST_METHOD, POST_METHOD);

            // SOAP
            if (method == null) {
                method = POST_METHOD;
            } else {
                message.put(EMPTY_REQUEST_PROPERTY, false);
                message.setContent(List.class, new MessageContentsList(new EmptyBody()));
                message.put(Type.class, EmptyBody.class);
                message.put("proxy.method.parameter.body.index", -1);
            }
        }

        message.put(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
        message.put(ACCEPT, APPLICATION_OCTET_STREAM);
        message.put(REQUEST_URI, vauUri);

        Object map = message.get(PROTOCOL_HEADERS);
        if (map == null) {
            map = new HashMap<String, List<String>>();
            message.put(PROTOCOL_HEADERS, map);
        }
        if (map instanceof Map headers) {
            headers.put(CONNECTION, List.of(KEEP_ALIVE));
            headers.put(CONTENT_TYPE, List.of(APPLICATION_OCTET_STREAM));
            headers.put(ACCEPT, List.of(APPLICATION_OCTET_STREAM));
            headers.put(VAU_NON_PU_TRACING, List.of(vauNonPUTracing));
            headers.put(VAU_METHOD_PATH, List.of((method == null ? "POST" : method) + " " + path));
            headers.put(VAU_CID, List.of(vauCid));

            Object vauNpObj = message.get(VAU_NP);
            if (vauNpObj != null) {
                String np = vauNpObj.toString();
                headers.put(VAU_NP, List.of(np));
            }
        }

        super.setupConnection(message, a, csPolicy);
    }

    @Override
    public void close(Message msg) throws IOException {
        Object soapVersion = msg.get("org.apache.cxf.binding.soap.SoapVersion");
        if (soapVersion != null) {
            HttpClient httpClient = msg.get(HttpClient.class);
            super.close(msg);
            msg.put(HttpClient.class, httpClient);
        } else {
            super.close(msg);
        }
    }
}
