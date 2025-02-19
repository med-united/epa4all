package de.servicehealth.epa4all.server.idp.vaunp;

import de.gematik.vau.lib.data.KdfKey2;
import de.service.health.api.epa4all.EpaConfig;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.cxf.provider.CborWriterProvider;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauInfo;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static de.servicehealth.utils.CborUtils.printCborMessage;
import static de.servicehealth.utils.ServerUtils.decompress;
import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK1_C2S;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK1_S2C;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK2_C2S_INFO;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK2_S2C_INFO;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT_ENCODING;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.HttpHeaders.HOST;
import static org.apache.cxf.helpers.HttpHeaderHelper.CONNECTION;

@Dependent
public class VauHandshake {

    private static final Logger log = LoggerFactory.getLogger(VauHandshake.class.getName());

    List<CborWriterProvider> providers = List.of(new CborWriterProvider());

    private final int connectionTimeoutMs;
    private final String epaUserAgent;

    @Inject
    public VauHandshake(VauConfig vauConfig, EpaConfig epaConfig) {
        connectionTimeoutMs = vauConfig.getConnectionTimeoutMs();
        epaUserAgent = epaConfig.getEpaUserAgent();
    }

    public void apply(String uri, VauClient vauClient) {
        try {
            TimeUnit.SECONDS.sleep(1);
            String mock = vauClient.isMock() ? "/" + Math.abs(vauClient.hashCode()) : "";
            M2Result m2Result = receiveM2(vauClient, uri, mock);
            receiveM4(vauClient, uri, mock, m2Result);
        } catch (Exception e) {
            log.error("Error while VAU handshake", e);
            throw new Fault(e);
        }
    }

    private static class M2Result {
        byte[] message2;
        String vauCid;

        public M2Result(byte[] message2, String vauCid) {
            this.message2 = message2;
            this.vauCid = vauCid;
        }
    }

    private M2Result receiveM2(VauClient vauClient, String uri, String mock) throws Exception {
        byte[] message1 = vauClient.generateMessage1();
        WebClient wc = prepareWebClient(
            uri,
            uri + mock + "/VAU",
            message1.length
        );
        Response response = wc.post(ByteBuffer.wrap(message1));
        byte[] message2 = getPayload(response);

        String vauCid = getHeaderValue(response, VAU_CID);
        String vauDebugSC = getHeaderValue(response, VAU_DEBUG_SK1_S2C);
        String vauDebugCS = getHeaderValue(response, VAU_DEBUG_SK1_C2S);
        String contentLength = getHeaderValue(response, CONTENT_LENGTH);
        printCborMessage(true, message2, vauCid, vauDebugSC, vauDebugCS, contentLength);
        
        return new M2Result(message2, vauCid);
    }

    private void receiveM4(VauClient vauClient, String uri, String mock, M2Result m2Result) throws Exception {
        byte[] message3 = vauClient.receiveMessage2(m2Result.message2);

        KdfKey2 clientKey2 = vauClient.getClientKey2();
        String c2sAppData = Base64.getEncoder().encodeToString(clientKey2.getClientToServerAppData());
        String s2cAppData = Base64.getEncoder().encodeToString(clientKey2.getServerToClientAppData());

        String vauCid = m2Result.vauCid;
        WebClient wc = prepareWebClient(
            uri,
            uri + mock + vauCid,
            message3.length
        );
        Response response = wc.post(ByteBuffer.wrap(message3));
        byte[] message4 = getPayload(response);

        String vauDebugSC = getHeaderValue(response, VAU_DEBUG_SK2_S2C_INFO);
        String vauDebugCS = getHeaderValue(response, VAU_DEBUG_SK2_C2S_INFO);
        String contentLength = getHeaderValue(response, CONTENT_LENGTH);
        printCborMessage(false, message4, null, vauDebugSC, vauDebugCS, contentLength);

        vauClient.receiveMessage4(message4);
        vauClient.setVauInfo(new VauInfo(mock + vauCid, c2sAppData, s2cAppData));
    }

    private WebClient prepareWebClient(
        String uri,
        String baseAddress,
        int messageLength
    ) throws Exception {
        WebClient wc = WebClient.create(baseAddress, providers);
        ClientConfiguration configuration = wc.getConfiguration();
        ClientFactory.initClient(configuration, connectionTimeoutMs, List.of(), List.of());
        wc.headers(prepareVauOutboundHeaders(epaUserAgent, uri, messageLength));
        return wc;
    }

    private byte[] getPayload(Response response) throws IOException {
        InputStream is = (InputStream) response.getEntity();
        return decompress(is.readAllBytes());
    }

    private String getHeaderValue(Response response, String headerName) {
        MultivaluedMap<String, Object> headers = response.getHeaders();
        return (String) headers.getFirst(headerName);
    }

    private MetadataMap<String, String> prepareVauOutboundHeaders(
        String epaUserAgent,
        String uri,
        int length
    ) {
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