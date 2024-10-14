package de.servicehealth.epa4all.cxf.interceptor;

import de.servicehealth.epa4all.VauClient;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.cxf.provider.CborWriterProvider;
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
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT_ENCODING;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.HttpHeaders.HOST;
import static jakarta.ws.rs.core.HttpHeaders.USER_AGENT;

public class CxfVauWriteInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger log = LoggerFactory.getLogger(CxfVauWriteInterceptor.class);

    public static final String VAU_CID = "VAU-CID";
    public static final String VAU_DEBUG_SK1_S2C = "VAU-DEBUG-S_K1_s2c";
    public static final String VAU_DEBUG_SK1_C2S = "VAU-DEBUG-S_K1_c2s";
    public static final String VAU_DEBUG_SK2_S2C_INFO = "VAU-DEBUG-S_K2_s2c_keyConfirmation";
    public static final String VAU_DEBUG_SK2_C2S_INFO = "VAU-DEBUG-S_K2_s2c_keyConfirmation";

    static {
        Security.addProvider(new BouncyCastlePQCProvider());
        Security.addProvider(new BouncyCastleProvider());
    }

    private final VauClient vauClient;

    public CxfVauWriteInterceptor(VauClient vauClient) {
        super(Phase.SETUP);
        this.vauClient = vauClient;
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        try {
            Conduit conduit = message.getExchange().getConduit(message);
            if (conduit instanceof HttpClientHTTPConduit) {
                if (vauClient.getVauCid() != null) {
                    String vauCid = vauClient.getVauCid();
                    message.put(VAU_CID, vauCid);
                } else {
                    String vauUri = (String) message.get("org.apache.cxf.message.Message.BASE_PATH");
                    String uri = vauUri.replace("+vau", "");

                    List<CborWriterProvider> providers = List.of(new CborWriterProvider());
                    WebClient client = WebClient.create(uri + "/VAU", providers);
                    ClientFactory.initClient(client.getConfiguration(), List.of(), List.of());

                    byte[] message1 = vauClient.getVauStateMachine().generateMessage1();

                    client.headers(prepareVauOutboundHeaders(uri, message1.length));

                    Response response = client.post(ByteBuffer.wrap(message1));
                    byte[] message2 = getPayload(response);

                    String vauCid = getHeader(response, VAU_CID);
                    String vauDebugSC = getHeader(response, VAU_DEBUG_SK1_S2C);
                    String vauDebugCS = getHeader(response, VAU_DEBUG_SK1_C2S);
                    String contentLength = getHeader(response, CONTENT_LENGTH);

                    printCborMessage(message2, vauCid, vauDebugSC, vauDebugCS, contentLength);

                    message.put(VAU_CID, vauCid);
                    message.put(VAU_DEBUG_SK1_S2C, getHeader(response, VAU_DEBUG_SK1_S2C));
                    message.put(VAU_DEBUG_SK1_C2S, getHeader(response, VAU_DEBUG_SK1_C2S));

                    byte[] message3 = vauClient.getVauStateMachine().receiveMessage2(message2);

                    // TODO path|query params for VAU endpoint as well
                    // epa-deployment/doc/html/MedicationFHIR.mhtml -> POST /1719478705211?_count=10&_offset=0&_total=none&_format=json

                    client = WebClient.create(uri + vauCid, providers);
                    ClientFactory.initClient(client.getConfiguration(), List.of(), List.of());
                    client.headers(prepareVauOutboundHeaders(uri, message3.length));

                    response = client.post(ByteBuffer.wrap(message3));
                    byte[] message4 = getPayload(response);
                    vauDebugSC = getHeader(response, VAU_DEBUG_SK2_S2C_INFO);
                    vauDebugCS = getHeader(response, VAU_DEBUG_SK2_C2S_INFO);
                    contentLength = getHeader(response, CONTENT_LENGTH);

                    printCborMessage(message4, null, vauDebugSC, vauDebugCS, contentLength);

                    vauClient.getVauStateMachine().receiveMessage4(message4);
                    vauClient.setVauCid(vauCid);
                }
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
        headers.add(ACCEPT, "application/octet-stream, application/json, application/cbor, application/*+json, */*");
        headers.add(ACCEPT_ENCODING, "gzip, x-gzip, deflate");
        headers.add(CONTENT_TYPE, "application/cbor");
        headers.add(CONTENT_LENGTH, String.valueOf(length));
        headers.add(HOST, URI.create(uri).getHost());
        headers.add(USER_AGENT, "Apache-CfxClient/4.0.5");
        return headers;
    }
}
