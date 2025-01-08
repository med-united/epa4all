package de.servicehealth.epa4all.medication.fhir.interceptor;

import de.gematik.vau.lib.data.KdfKey2;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import de.servicehealth.vau.VauInfo;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpMessage;
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

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.servicehealth.utils.CborUtils.printCborMessage;
import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK1_C2S;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK1_S2C;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK2_C2S_INFO;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK2_S2C_INFO;
import static de.servicehealth.vau.VauClient.VAU_NON_PU_TRACING;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;
import static de.servicehealth.vau.VauFacade.NO_USER_SESSION;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpHeaders.ACCEPT_ENCODING;
import static org.apache.http.HttpHeaders.CONNECTION;
import static org.apache.http.HttpHeaders.CONTENT_LENGTH;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpHeaders.HOST;
import static org.apache.http.client.fluent.Executor.newInstance;

public class FHIRRequestVAUInterceptor implements HttpRequestInterceptor {

    private static final Logger log = Logger.getLogger(FHIRRequestVAUInterceptor.class.getName());

    static {
        Security.addProvider(new BouncyCastlePQCProvider());
        Security.addProvider(new BouncyCastleProvider());
    }

    private final URI medicationBaseUri;
    private final String epaUserAgent;
    private final SSLContext sslContext;
    private final VauFacade vauFacade;

    public FHIRRequestVAUInterceptor(
        URI medicationBaseUri,
        String epaUserAgent,
        SSLContext sslContext,
        VauFacade vauFacade
    ) {
        this.medicationBaseUri = medicationBaseUri;
        this.epaUserAgent = epaUserAgent;
        this.sslContext = sslContext;
        this.vauFacade = vauFacade;
    }

    @Override
    public void process(HttpRequest request, HttpContext context) {
        if (request instanceof HttpEntityEnclosingRequest entityRequest) {
            boolean encrypted = false;
            String vauCid = null;
            try {
                VauClient vauClient = initVauClient();
                VauInfo vauInfo = vauClient.getVauInfo();
                vauCid = vauInfo.getVauCid();

                byte[] vauMessage;
                try {
                    vauMessage = prepareVauMessage(entityRequest, vauClient);
                } finally {
                    encrypted = true;
                }
                entityRequest.setEntity(new ByteArrayEntity(vauMessage));
                entityRequest.setHeader(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
                entityRequest.setHeader(ACCEPT, APPLICATION_OCTET_STREAM);

                entityRequest.removeHeaders(X_INSURANT_ID);

                if (vauFacade.isTracingEnabled()) {
                    entityRequest.setHeader(VAU_NON_PU_TRACING, vauInfo.getVauNonPUTracing());
                }

                String port = getMedicationPort();
                String vauUri = medicationBaseUri.getScheme() + "://" + medicationBaseUri.getHost() + port + vauCid;
                ((HttpRequestWrapper) entityRequest).setURI(URI.create(vauUri));

            } catch (Exception e) {
                log.log(Level.SEVERE, "Error while sending DIRECT Fhir request, encrypted = " + encrypted, e);
                if (encrypted) {
                    boolean noUserSession = e.getMessage().contains(NO_USER_SESSION);
                    vauFacade.steadyVauSession(vauCid, noUserSession, false);
                }
                throw new RuntimeException(e);
            }
        }
    }

    private String getMedicationPort() {
        return medicationBaseUri.getPort() < 0 ? "" : ":" + medicationBaseUri.getPort();
    }

    private byte[] prepareVauMessage(HttpEntityEnclosingRequest request, VauClient vauClient) throws IOException {
        HttpEntity entity = request.getEntity();
        byte[] body = entity == null ? new byte[0] : entity.getContent().readAllBytes();

        String method = body.length == 0 ? "GET" : "POST"; // TODO enhance
        String path = URLDecoder.decode(request.getRequestLine().getUri(), StandardCharsets.UTF_8);

        boolean api = path.contains("api");
        String additionalHeaders = Stream.of(request.getAllHeaders())
            .filter(h -> !h.getName().equalsIgnoreCase(CONTENT_TYPE))
            .filter(h -> !h.getName().equalsIgnoreCase(X_BACKEND))
            .filter(h -> !h.getName().equalsIgnoreCase(api ? ACCEPT : ""))
            .map(h -> h.getName() + ": " + h.getValue())
            .collect(Collectors.joining("\r\n"));

        if (!additionalHeaders.isBlank()) {
            additionalHeaders += "\r\n";
        }

        String backend = getHeaderValue(request, X_BACKEND);
        request.removeHeader(request.getFirstHeader(X_BACKEND));
        String contentType = body.length == 0
            ? "\r\n" // extra blank line
            : "Content-Type: application/fhir+json; charset=UTF-8\r\nContent-Length: " + body.length + "\r\n\r\n";

        byte[] httpRequest = (method + " " + path + " HTTP/1.1\r\n"
            + "Host: " + backend + "\r\n"
            + additionalHeaders
            + prepareAcceptHeader(api, body)
            + contentType
        ).getBytes();

        byte[] content = ArrayUtils.addAll(httpRequest, body);
        log.info("FHIR REQUEST: " + new String(content));
        return vauClient.encryptVauMessage(content);
    }

    private String prepareAcceptHeader(boolean api, byte[] body) {
        if (api) {
            return body.length != 0
                ? "Accept: application/fhir+xml;q=1.0, application/fhir+json;q=1.0, application/xml+fhir;q=0.9, application/json+fhir;q=0.9\r\n"
                : "Accept: application/fhir+json;q=1.0, application/json+fhir;q=0.9\r\n";
        } else {
            return "";
        }
    }

    private VauClient initVauClient() throws Exception {
        VauClient vauClient = vauFacade.acquireVauClient();
        if (vauClient.getVauInfo() != null) {
            return vauClient;
        }

        String host = medicationBaseUri.getHost();

        byte[] message1 = vauClient.generateMessage1();
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

        String vauCid = getHeaderValue(httpResponse, VAU_CID);
        String vauDebugCS = getHeaderValue(httpResponse, VAU_DEBUG_SK1_C2S);
        String vauDebugSC = getHeaderValue(httpResponse, VAU_DEBUG_SK1_S2C);
        String contentLength = getHeaderValue(httpResponse, CONTENT_LENGTH);

        byte[] message2 = httpResponse.getEntity().getContent().readAllBytes();

        printCborMessage(message2, vauCid, vauDebugSC, vauDebugCS, contentLength);

        byte[] message3 = vauClient.receiveMessage2(message2);

        KdfKey2 clientKey2 = vauClient.getClientKey2();
        String c2sAppData = Base64.getEncoder().encodeToString(clientKey2.getClientToServerAppData());
        String s2cAppData = Base64.getEncoder().encodeToString(clientKey2.getServerToClientAppData());

        Request request2 = Request
            .Post(medicationBaseUri.getScheme() + "://" + host + port + vauCid)
            .bodyByteArray(message3)
            .setHeaders(prepareHeaders(host));

        response = executor.execute(request2);
        httpResponse = response.returnResponse();

        vauDebugCS = getHeaderValue(httpResponse, VAU_DEBUG_SK2_C2S_INFO);
        vauDebugSC = getHeaderValue(httpResponse, VAU_DEBUG_SK2_S2C_INFO);
        contentLength = getHeaderValue(httpResponse, CONTENT_LENGTH);

        VauInfo vauInfo = new VauInfo(vauCid, c2sAppData, s2cAppData);

        byte[] message4 = httpResponse.getEntity().getContent().readAllBytes();

        printCborMessage(message4, null, vauDebugSC, vauDebugCS, contentLength);

        vauClient.receiveMessage4(message4);
        vauClient.setVauInfo(vauInfo);

        return vauClient;
    }

    private String getHeaderValue(HttpMessage httpMessage, String headerName) {
        Header header = httpMessage.getFirstHeader(headerName);
        return header == null ? null : header.getValue();
    }

    private Header[] prepareHeaders(String host) {
        Header[] headers = new Header[6];
        headers[0] = new BasicHeader(CONNECTION, "Keep-Alive");
        headers[1] = new BasicHeader(ACCEPT, "application/octet-stream, application/json, application/cbor, application/*+json, */*");
        headers[2] = new BasicHeader(ACCEPT_ENCODING, "gzip, x-gzip, deflate");
        headers[3] = new BasicHeader(CONTENT_TYPE, "application/cbor");
        headers[4] = new BasicHeader(HOST, host);
        headers[5] = new BasicHeader(X_USER_AGENT, epaUserAgent);
        return headers;
    }
}