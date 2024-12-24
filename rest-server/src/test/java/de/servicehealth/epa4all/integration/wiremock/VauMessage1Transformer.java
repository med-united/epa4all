package de.servicehealth.epa4all.integration.wiremock;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.gematik.vau.lib.VauServerStateMachine;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.concurrent.ConcurrentHashMap;

import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK1_C2S;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK1_S2C;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static org.apache.cxf.helpers.HttpHeaderHelper.CONTENT_TYPE;

@Getter
public class VauMessage1Transformer implements ResponseDefinitionTransformerV2 {

    @Getter
    @AllArgsConstructor
    private static class VauServerInfo {
        private String uniquePath;
        private VauServerStateMachine vauServer;
    }

    private static final ConcurrentHashMap<String, VauServerInfo> vau1ServersMap = new ConcurrentHashMap<>();

    private final String name;

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
        vau1ServersMap.put(path, new VauServerInfo(uniquePath, vauServer));
    }

    @Override
    public ResponseDefinition transform(ServeEvent serveEvent) {
        LoggedRequest request = serveEvent.getRequest();
        VauServerInfo vauServerInfo = vau1ServersMap.get(request.getUrl());
        VauServerStateMachine vauServer = vauServerInfo.getVauServer();
        String uniquePath = vauServerInfo.getUniquePath();

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
