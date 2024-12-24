package de.servicehealth.epa4all.integration.wiremock;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
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
public class VauMessage1Transformer implements ResponseDefinitionTransformerV2 {

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
    public ResponseDefinition transform(ServeEvent serveEvent) {
        LoggedRequest request = serveEvent.getRequest();
        VauServerStateMachine vauServer = vau1ServersMap.get(request.getUrl());

        byte[] message1 = request.getBody();
        byte[] message2 = vauServer.receiveMessage(message1);

        return new ResponseDefinitionBuilder()
            .withHeader(VAU_CID, "/VAU/" + uniquePath)
            .withHeader(VAU_DEBUG_SK1_S2C, "")
            .withHeader(VAU_DEBUG_SK1_C2S, "")
            .withHeader(CONTENT_TYPE, "application/cbor")
            .withHeader(CONTENT_LENGTH, String.valueOf(message2.length))
            .withStatus(200)
            .withBody(message2)
            .build();
    }
}
