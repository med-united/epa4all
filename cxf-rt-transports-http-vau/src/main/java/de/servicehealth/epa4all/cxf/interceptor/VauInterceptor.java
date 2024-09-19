package de.servicehealth.epa4all.cxf.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import de.gematik.vau.lib.VauClientStateMachine;
import de.servicehealth.epa4all.cxf.provider.CborProvider;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.apache.commons.codec.binary.Base64;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.http.HttpClientHTTPConduit;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

import java.io.InputStream;
import java.security.Security;
import java.util.List;

import static de.servicehealth.epa4all.cxf.utils.TransportUtils.initApi;

public class VauInterceptor extends AbstractPhaseInterceptor<Message> {

    static {
        Security.addProvider(new BouncyCastlePQCProvider());
        Security.addProvider(new BouncyCastleProvider());
    }

    private final VauClientStateMachine vauClient;

    public VauInterceptor(VauClientStateMachine vauClient) {
        super(Phase.PRE_STREAM);
        this.vauClient = vauClient;
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        try {
            Conduit conduit = message.getExchange().getConduit(message);
            if (conduit instanceof HttpClientHTTPConduit httpConduit) {

                List<CborProvider> providers = List.of(new CborProvider());
                WebClient client = WebClient.create("https://localhost:443/VAU", providers);
                initApi(client, List.of());

                byte[] message1 = vauClient.generateMessage1();

                client.header(HttpHeaders.ACCEPT, "application/octet-stream, application/json, application/cbor, application/*+json, */*");
                client.header(HttpHeaders.ACCEPT_ENCODING, "gzip, x-gzip, deflate");
                client.header(HttpHeaders.CONTENT_TYPE, "application/cbor");
                client.header(HttpHeaders.CONTENT_LENGTH, message1.length);
                client.header(HttpHeaders.HOST, "localhost:443");
                client.header("Connection", "keep-alive");
                client.header(HttpHeaders.USER_AGENT, "Apache-CfxClient/4.0.5");

                Response response = client.post(message1);

                InputStream is = (InputStream) response.getEntity();
                final JsonNode message2Tree = new CBORMapper().readTree(is.readAllBytes());

                MultivaluedMap<String, Object> headers = response.getHeaders();
                System.out.println(headers);

                System.out.println("MESSAGE 2 from VAU, length = " + headers.get(HttpHeaders.CONTENT_LENGTH));

                System.out.println("MessageType: " + message2Tree.get("MessageType").textValue());
                System.out.println();
                System.out.println("Kyber768_ct: " + new String(Base64.encodeBase64(message2Tree.get("Kyber768_ct").binaryValue())));
                System.out.println();
                System.out.println("AEAD_ct: " + new String(Base64.encodeBase64(message2Tree.get("AEAD_ct").binaryValue())));
                System.out.println();
                System.out.println("ECDH_ct: " + message2Tree.get("ECDH_ct").toString());
                System.out.println();

            }
        } catch (Exception e) {
            throw new Fault(e);
        }
    }
}
