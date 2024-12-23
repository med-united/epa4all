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

import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK2_C2S_INFO;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK2_S2C_INFO;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static org.jose4j.jwx.HeaderParameterNames.CONTENT_TYPE;

@Getter
public class VauMessage2Transformer implements ResponseTransformerV2 {

    private static final ConcurrentHashMap<String, VauServerStateMachine> vau2ServersMap = new ConcurrentHashMap<>();

    private final String name;

    public VauMessage2Transformer(String name) {
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

    public void registerVauChannel(String path, VauServerStateMachine vauServer) {
        vau2ServersMap.put(path, vauServer);
    }

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
        LoggedRequest request = serveEvent.getRequest();
        VauServerStateMachine vauServer = vau2ServersMap.get(request.getUrl());

        byte[] message3 = serveEvent.getRequest().getBody();
        byte[] message4 = vauServer.receiveMessage(message3);

        return Response.response()
            .headers(new HttpHeaders(
                new HttpHeader(VAU_DEBUG_SK2_S2C_INFO, ""),
                new HttpHeader(VAU_DEBUG_SK2_C2S_INFO, ""),
                new HttpHeader(CONTENT_TYPE, "application/cbor"),
                new HttpHeader(CONTENT_LENGTH, String.valueOf(message4.length))
            ))
            .body(message4).status(200).build();
    }
}
