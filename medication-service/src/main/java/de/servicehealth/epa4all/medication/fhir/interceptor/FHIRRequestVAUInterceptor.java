package de.servicehealth.epa4all.medication.fhir.interceptor;

import de.servicehealth.vau.VauClient;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.security.Security;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.servicehealth.utils.CborUtils.printCborMessage;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpHeaders.ACCEPT_ENCODING;
import static org.apache.http.HttpHeaders.CONNECTION;
import static org.apache.http.HttpHeaders.CONTENT_LENGTH;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpHeaders.HOST;
import static org.apache.http.HttpHeaders.USER_AGENT;
import static org.apache.http.client.fluent.Executor.newInstance;

public class FHIRRequestVAUInterceptor implements HttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FHIRRequestVAUInterceptor.class);

    public static final String VAU_CID = "VAU-CID";
    public static final String VAU_DEBUG_SK1_S2C = "VAU-DEBUG-S_K1_s2c";
    public static final String VAU_DEBUG_SK1_C2S = "VAU-DEBUG-S_K1_c2s";
    public static final String VAU_DEBUG_SK2_S2C_INFO = "VAU-DEBUG-S_K2_s2c_keyConfirmation";
    public static final String VAU_DEBUG_SK2_C2S_INFO = "VAU-DEBUG-S_K2_s2c_keyConfirmation";

    static {
        Security.addProvider(new BouncyCastlePQCProvider());
        Security.addProvider(new BouncyCastleProvider());
    }

    private final URI medicationBaseUri;
    private final SSLContext sslContext;
    private final VauClient vauClient;

    public FHIRRequestVAUInterceptor(URI medicationBaseUri, SSLContext sslContext, VauClient vauClient) {
        this.medicationBaseUri = medicationBaseUri;
        this.sslContext = sslContext;
        this.vauClient = vauClient;
    }

    @Override
    public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
        if (request instanceof HttpEntityEnclosingRequest entityRequest) {
            try {
                String vauCid = initVau();
                byte[] vauMessage = prepareVauMessage(entityRequest);
                entityRequest.setEntity(new ByteArrayEntity(vauMessage));
                entityRequest.setHeader(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
                entityRequest.setHeader(ACCEPT, APPLICATION_OCTET_STREAM);

                String port = getMedicationPort();
                String vauUri = medicationBaseUri.getScheme() + "://" + medicationBaseUri.getHost() + port + vauCid;
                ((HttpRequestWrapper) entityRequest).setURI(URI.create(vauUri));

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String getMedicationPort() {
        return medicationBaseUri.getPort() < 0 ? "" : ":" + medicationBaseUri.getPort();
    }

    private byte[] prepareVauMessage(HttpEntityEnclosingRequest request) throws IOException {
        Header[] headers = request.getAllHeaders();
        HttpEntity entity = request.getEntity();
        byte[] body = entity == null ? new byte[0] : entity.getContent().readAllBytes();

        String contentType = body.length == 0
            ? "\r\n" // extra blank line
            : "Content-Type: application/fhir+json; charset=UTF-8\r\nContent-Length: " + body.length + "\r\n\r\n";

        String method = body.length == 0 ? "GET" : "POST"; // TODO enhance
        String path = request.getRequestLine().getUri();

        boolean api = path.contains("api");
        String additionalHeaders = Stream.of(headers)
            .filter(h -> !h.getName().equals(CONTENT_TYPE))
            .filter(h -> !h.getName().equals(api ? ACCEPT : ""))
            .map(h -> h.getName() + ": " + h.getValue())
            .collect(Collectors.joining("\r\n"));

        if (!additionalHeaders.isBlank()) {
            additionalHeaders += "\r\n";
        }

        byte[] httpRequest = (method + " " + path + " HTTP/1.1\r\n"
            + "Host: medication-service:8080\r\n" // TODO
            + additionalHeaders
            + prepareAcceptHeader(api, body)
            + contentType).getBytes();

        byte[] content = ArrayUtils.addAll(httpRequest, body);

        return vauClient.getVauStateMachine().encryptVauMessage(content);
    }

    private String prepareAcceptHeader(boolean api, byte[] body) {
        if (api) {
           return body.length == 0
               ? "Accept: application/fhir+xml;q=1.0, application/fhir+json;q=1.0, application/xml+fhir;q=0.9, application/json+fhir;q=0.9\r\n"
               : "Accept: application/fhir+json;q=1.0, application/json+fhir;q=0.9\r\n";
        } else {
            return "";
        }
    }

    private String initVau() throws Exception {
        if (vauClient.getVauCid() != null) {
            return vauClient.getVauCid();
        }

        String host = medicationBaseUri.getHost();

        byte[] message1 = vauClient.getVauStateMachine().generateMessage1();
        String port = getMedicationPort();
        Request request1 = Request
            .Post(medicationBaseUri.getScheme() + "://" + host + port + "/VAU")
            .bodyByteArray(message1)
            .setHeaders(prepareHeaders(host));

        Executor executor = newInstance(HttpClients.custom()
            .setDefaultRequestConfig(RequestConfig.custom().setRedirectsEnabled(false).build())
            .setSSLContext(sslContext)
            .build());

        Response response = executor.execute(request1);
        HttpResponse httpResponse = response.returnResponse();
        String vauCid = httpResponse.getFirstHeader(VAU_CID).getValue();
        Header firstHeader = httpResponse.getFirstHeader(VAU_DEBUG_SK1_S2C);
		String vauDebugSC = firstHeader != null ? firstHeader.getValue() : null;
        Header firstHeader2 = httpResponse.getFirstHeader(VAU_DEBUG_SK1_C2S);
		String vauDebugCS = firstHeader2 != null ? firstHeader2.getValue() : null;
        Header firstHeader3 = httpResponse.getFirstHeader(CONTENT_LENGTH);
		String contentLength = firstHeader3 != null ? firstHeader3.getValue() : null;
        byte[] message2 = httpResponse.getEntity().getContent().readAllBytes();

        printCborMessage(message2, vauCid, vauDebugSC, vauDebugCS, contentLength);

        byte[] message3 = vauClient.getVauStateMachine().receiveMessage2(message2);

        Request request2 = Request
            .Post(medicationBaseUri.getScheme() + "://" + host + port + vauCid)
            .bodyByteArray(message3)
            .setHeaders(prepareHeaders(host));

        response = executor.execute(request2);
        httpResponse = response.returnResponse();
        Header firstHeader4 = httpResponse.getFirstHeader(VAU_DEBUG_SK2_S2C_INFO);
		String vauDebugSCInfo = firstHeader4 != null ? firstHeader4.getValue() : null;
        Header firstHeader5 = httpResponse.getFirstHeader(VAU_DEBUG_SK2_C2S_INFO);
		String vauDebugCSInfo = firstHeader5 != null ? firstHeader5.getValue() : null;
        Header firstHeader32 = httpResponse.getFirstHeader(CONTENT_LENGTH);
		contentLength = firstHeader32 != null ? firstHeader32.getValue() : null;
        byte[] message4 = httpResponse.getEntity().getContent().readAllBytes();

        printCborMessage(message4, null, vauDebugSCInfo, vauDebugCSInfo, contentLength);

        vauClient.getVauStateMachine().receiveMessage4(message4);
        vauClient.setVauCid(vauCid);
        return vauCid;
    }

    private Header[] prepareHeaders(String host) {
        Header[] headers = new Header[6];
        headers[0] = new BasicHeader(CONNECTION, "Keep-Alive");
        headers[1] = new BasicHeader(ACCEPT, "application/octet-stream, application/json, application/cbor, application/*+json, */*");
        headers[2] = new BasicHeader(ACCEPT_ENCODING, "gzip, x-gzip, deflate");
        headers[3] = new BasicHeader(CONTENT_TYPE, "application/cbor");
        headers[4] = new BasicHeader(HOST, host);
        headers[5] = new BasicHeader(USER_AGENT, "Apache-CfxClient/4.0.5");
        return headers;
    }
}