package de.servicehealth.epa4all.cxf.interceptor;

import de.gematik.vau.lib.data.KdfKey2;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.cxf.provider.CborWriterProvider;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import de.servicehealth.vau.VauInfo;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
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
import java.util.Base64;
import java.util.List;

import static de.servicehealth.utils.CborUtils.printCborMessage;
import static de.servicehealth.utils.ServerUtils.decompress;
import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK1_C2S;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK1_S2C;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK2_C2S_INFO;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK2_S2C_INFO;
import static de.servicehealth.vau.VauClient.VAU_NON_PU_TRACING;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT_ENCODING;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.HttpHeaders.HOST;
import static org.apache.cxf.helpers.HttpHeaderHelper.CONNECTION;

public class CxfVauSetupInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger log = LoggerFactory.getLogger(CxfVauSetupInterceptor.class.getName());

    static {
        Security.addProvider(new BouncyCastlePQCProvider());
        Security.addProvider(new BouncyCastleProvider());
    }

    private final VauConfig vauConfig;
    private final VauFacade vauFacade;
    private final String epaUserAgent;

    public CxfVauSetupInterceptor(VauFacade vauFacade, VauConfig vauConfig, String epaUserAgent) {
        super(Phase.SETUP);
        this.vauConfig = vauConfig;
        this.vauFacade = vauFacade;
        this.epaUserAgent = epaUserAgent;
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        try {
            Conduit conduit = message.getExchange().getConduit(message);
            if (conduit instanceof HttpClientHTTPConduit) {
                VauClient vauClient = vauFacade.acquireVauClient();
                VauInfo vauInfo = vauClient.getVauInfo();
                if (vauInfo != null) {
                    message.put(VAU_CID, vauInfo.getVauCid());
                    message.put(VAU_NON_PU_TRACING, vauInfo.getVauNonPUTracing());
                } else {

                    // TODO - refactor!

                    String vauUri = (String) message.get("org.apache.cxf.message.Message.BASE_PATH");
                    if (vauUri == null) {
                        vauUri = (String) message.get("org.apache.cxf.message.Message.ENDPOINT_ADDRESS");
                    }
                    String uri = vauUri.replace("+vau", "");
                    URI uriObject = new URI(uri);
                    // Construct the base URI
                    uri = uriObject.getScheme() + "://" + uriObject.getHost() + (uriObject.getPort() == -1 ? "" : ":" + uriObject.getPort());

                    String mock = vauClient.isMock() ? "/" + Math.abs(vauClient.hashCode()) : "";

                    int connectionTimeoutMs = vauConfig.getConnectionTimeoutMs();

                    List<CborWriterProvider> providers = List.of(new CborWriterProvider());
                    String baseAddress = uri + mock + "/VAU";
                    WebClient client1 = WebClient.create(baseAddress, providers);
                    ClientConfiguration configuration = client1.getConfiguration();
                    ClientFactory.initClient(configuration, connectionTimeoutMs, List.of(), List.of());

                    byte[] message1 = vauClient.generateMessage1();
                    client1.headers(prepareVauOutboundHeaders(uri, message1.length));
                    Response response = client1.post(ByteBuffer.wrap(message1));
                    byte[] message2 = getPayload(response);

                    String vauCid = getHeaderValue(response, VAU_CID);
                    String vauDebugSC = getHeaderValue(response, VAU_DEBUG_SK1_S2C);
                    String vauDebugCS = getHeaderValue(response, VAU_DEBUG_SK1_C2S);
                    String contentLength = getHeaderValue(response, CONTENT_LENGTH);

                    printCborMessage(true, message2, vauCid, vauDebugSC, vauDebugCS, contentLength);

                    byte[] message3 = vauClient.receiveMessage2(message2);

                    KdfKey2 clientKey2 = vauClient.getClientKey2();
                    String c2sAppData = Base64.getEncoder().encodeToString(clientKey2.getClientToServerAppData());
                    String s2cAppData = Base64.getEncoder().encodeToString(clientKey2.getServerToClientAppData());

                    // TODO path|query params for VAU endpoint as well
                    // epa-deployment/doc/html/MedicationFHIR.mhtml -> POST /1719478705211?_count=10&_offset=0&_total=none&_format=json

                    WebClient client2 = WebClient.create(uri + mock + vauCid, providers);
                    ClientFactory.initClient(client2.getConfiguration(), connectionTimeoutMs, List.of(), List.of());
                    client2.headers(prepareVauOutboundHeaders(uri, message3.length));

                    Response response2 = client2.post(ByteBuffer.wrap(message3));
                    byte[] message4 = getPayload(response2);

                    vauDebugSC = getHeaderValue(response2, VAU_DEBUG_SK2_S2C_INFO);
                    vauDebugCS = getHeaderValue(response2, VAU_DEBUG_SK2_C2S_INFO);
                    contentLength = getHeaderValue(response2, CONTENT_LENGTH);

                    printCborMessage(false, message4, null, vauDebugSC, vauDebugCS, contentLength);

                    vauClient.receiveMessage4(message4);

                    vauInfo = new VauInfo(mock + vauCid, c2sAppData, s2cAppData);
                    message.put(VAU_CID, mock + vauCid);
                    message.put(VAU_NON_PU_TRACING, vauInfo.getVauNonPUTracing());
                    vauClient.setVauInfo(vauInfo);
                }
            }
        } catch (Exception e) {
            log.error("Error while VAU handshake", e);
            throw new Fault(e);
        }
    }

    private byte[] getPayload(Response response) throws IOException {
        InputStream is = (InputStream) response.getEntity();
        return decompress(is.readAllBytes());
    }

    private String getHeaderValue(Response response, String headerName) {
        MultivaluedMap<String, Object> headers = response.getHeaders();
        return (String) headers.getFirst(headerName);
    }

    private void printHeaders(Response response) {
        MultivaluedMap<String, Object> headers = response.getHeaders();
        log.info("Response.Headers:");
        headers.keySet().forEach(key -> log.info(key + " -> " + headers.getFirst(key)));
    }

    private MetadataMap<String, String> prepareVauOutboundHeaders(String uri, int length) {
        MetadataMap<String, String> headers = new MetadataMap<>();
        headers.add(CONNECTION, "Keep-Alive");
        headers.add(ACCEPT, "application/octet-stream, application/json, application/cbor, application/*+json, */*");
        headers.add(ACCEPT_ENCODING, "gzip, x-gzip, deflate");
        headers.add(CONTENT_TYPE, "application/cbor");
        headers.add(CONTENT_LENGTH, String.valueOf(length));
        headers.add(HOST, URI.create(uri).getHost());
        headers.add(X_USER_AGENT, epaUserAgent);
        return headers;
    }
}
