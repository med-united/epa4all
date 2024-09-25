package de.servicehealth.epa4all.cxf.interceptor;

import de.gematik.vau.lib.VauClientStateMachine;
import de.servicehealth.epa4all.cxf.provider.CBORProvider;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.http.HttpClientHTTPConduit;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.Security;
import java.util.List;

import static de.servicehealth.epa4all.TransportUtils.printCborMessage;
import static de.servicehealth.epa4all.cxf.utils.CxfUtils.initClient;

public class CXFVAUInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger log = LoggerFactory.getLogger(CXFVAUInterceptor.class);

    public static final String VAU_CID = "VAU-CID";
    public static final String VAU_DEBUG_SK1_S2C = "VAU-DEBUG-S_K1_s2c";
    public static final String VAU_DEBUG_SK1_C2S = "VAU-DEBUG-S_K1_c2s";
    public static final String VAU_DEBUG_SK2_S2C_INFO = "VAU-DEBUG-S_K2_s2c_keyConfirmation";
    public static final String VAU_DEBUG_SK2_C2S_INFO = "VAU-DEBUG-S_K2_s2c_keyConfirmation";

    static {
        Security.addProvider(new BouncyCastlePQCProvider());
        Security.addProvider(new BouncyCastleProvider());
    }

    private final VauClientStateMachine vauClient;

    public CXFVAUInterceptor(VauClientStateMachine vauClient) {
        super(Phase.SETUP);
        this.vauClient = vauClient;
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        try {
            Conduit conduit = message.getExchange().getConduit(message);
            if (conduit instanceof HttpClientHTTPConduit) {

                String vauUri = (String) message.get("org.apache.cxf.message.Message.BASE_PATH");
                String uri = vauUri.replace("+vau", "");

                List<CBORProvider> providers = List.of(new CBORProvider());
                WebClient client = WebClient.create(uri + "/VAU", providers);
                initClient(client, List.of());

                byte[] message1 = vauClient.generateMessage1();

                client.headers(prepareVauOutboundHeaders(uri, message1.length));

                Response response = client.post(ByteBuffer.wrap(message1));
                byte[] message2 = getPayload(response);

                String vauCid = getHeader(response, VAU_CID);
                String vauDebugSC = getHeader(response, VAU_DEBUG_SK1_S2C);
                String vauDebugCS = getHeader(response, VAU_DEBUG_SK1_C2S);
                String contentLength = getHeader(response, HttpHeaders.CONTENT_LENGTH);

                printCborMessage(message2, vauCid, vauDebugSC, vauDebugCS, contentLength);

                message.put(VAU_CID, vauCid);
                message.put(VAU_DEBUG_SK1_S2C, getHeader(response, VAU_DEBUG_SK1_S2C));
                message.put(VAU_DEBUG_SK1_C2S, getHeader(response, VAU_DEBUG_SK1_C2S));

                byte[] message3 = vauClient.receiveMessage2(message2);

                client = WebClient.create(uri + vauCid, providers);
                initClient(client, List.of());
                client.headers(prepareVauOutboundHeaders(uri, message3.length));

                response = client.post(ByteBuffer.wrap(message3));
                byte[] message4 = getPayload(response);
                vauDebugSC = getHeader(response, VAU_DEBUG_SK2_S2C_INFO);
                vauDebugCS = getHeader(response, VAU_DEBUG_SK2_C2S_INFO);
                contentLength = getHeader(response, HttpHeaders.CONTENT_LENGTH);

                printCborMessage(message4, null, vauDebugSC, vauDebugCS, contentLength);

                vauClient.receiveMessage4(message4);
            }
        } catch (Exception e) {
            throw new Fault(e);
        }
    }

    private byte[] getPayload(Response response) throws IOException {
        InputStream is = (InputStream) response.getEntity();
        return is.readAllBytes();
    }

    private String getHeader(Response response, String name) {
        MultivaluedMap<String, Object> headers = response.getHeaders();
        return (String) headers.getFirst(name);
    }

    private MetadataMap<String, String> prepareVauOutboundHeaders(String uri, int length) {
        MetadataMap<String, String> headers = new MetadataMap<>();
        headers.add("Connection", "Keep-Alive");
        headers.add(HttpHeaders.ACCEPT, "application/octet-stream, application/json, application/cbor, application/*+json, */*");
        headers.add(HttpHeaders.ACCEPT_ENCODING, "gzip, x-gzip, deflate");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/cbor");
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(length));
        headers.add(HttpHeaders.HOST, URI.create(uri).getHost());
        headers.add(HttpHeaders.USER_AGENT, "Apache-CfxClient/4.0.5");
        return headers;
    }
}
