package de.servicehealth.epa4all.integration.wiremock;

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.gematik.vau.lib.VauServerStateMachine;
import lombok.Getter;

import java.util.concurrent.ConcurrentHashMap;

import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK1_C2S;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK1_S2C;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static org.apache.cxf.helpers.HttpHeaderHelper.CONTENT_TYPE;

@Getter
public class VauMessage1Transformer implements ResponseTransformerV2 {

    private static final ConcurrentHashMap<String, VauServerStateMachine> vau1ServersMap = new ConcurrentHashMap<>();

    private final String name;

    private String uniquePath;

    public VauMessage1Transformer(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    public void registerVauChannel(String path, String uniquePath, VauServerStateMachine vauServer) {
        this.uniquePath = uniquePath;
        vau1ServersMap.put(path, vauServer);
    }

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
        LoggedRequest request = serveEvent.getRequest();
        VauServerStateMachine vauServer = vau1ServersMap.get(request.getUrl());

        byte[] message1 = request.getBody();
        byte[] message2 = vauServer.receiveMessage(message1);

        try {
            return Response.response()
                .headers(new HttpHeaders(
                    new HttpHeader(VAU_CID, "/VAU/" + uniquePath),
                    new HttpHeader(VAU_DEBUG_SK1_S2C, ""),
                    new HttpHeader(VAU_DEBUG_SK1_C2S, ""),
                    new HttpHeader(CONTENT_TYPE, "application/cbor"),
                    new HttpHeader(CONTENT_LENGTH, String.valueOf(message2.length))
                ))
                .body(message2).status(200).build();
        } catch (Throwable t) {
            return Response.response()
                .status(500)
                .body("Error processing request: " + t.getMessage())
                .build();
        }
    }
}
