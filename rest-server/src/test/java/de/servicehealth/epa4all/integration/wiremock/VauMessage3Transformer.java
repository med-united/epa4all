package de.servicehealth.epa4all.integration.wiremock;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
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
public class VauMessage3Transformer implements ResponseDefinitionTransformerV2 {

    private static final ConcurrentHashMap<String, VauServerStateMachine> vau2ServersMap = new ConcurrentHashMap<>();

    private final String name;

    public VauMessage3Transformer(String name) {
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
    public ResponseDefinition transform(ServeEvent serveEvent) {
        LoggedRequest request = serveEvent.getRequest();
        VauServerStateMachine vauServer = vau2ServersMap.get(request.getUrl());

        byte[] message3 = serveEvent.getRequest().getBody();

        // TODO analyze message3 -> chain

        byte[] message4 = vauServer.receiveMessage(message3);

        return new ResponseDefinitionBuilder()
            .withHeader(VAU_DEBUG_SK2_S2C_INFO, "")
            .withHeader(VAU_DEBUG_SK2_C2S_INFO, "")
            .withHeader(CONTENT_TYPE, "application/cbor")
            .withHeader(CONTENT_LENGTH, String.valueOf(message4.length))
            .withStatus(200)
            .withBody(message4)
            .build();
    }
}
