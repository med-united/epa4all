package de.servicehealth.epa4all.integration.bc.wiremock;

import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.gematik.vau.lib.VauServerStateMachine;
import de.servicehealth.http.HttpParcel;
import de.servicehealth.vau.VauFacade;
import de.servicehealth.vau.VauResponseReader;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static de.servicehealth.epa4all.common.TestUtils.getResourcePath;
import static de.servicehealth.epa4all.integration.bc.wiremock.RequestVauNpIT.FIXTURES;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK2_C2S_INFO;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK2_S2C_INFO;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.HttpHeaders.LOCATION;
import static java.nio.charset.StandardCharsets.UTF_8;

@Getter
public class VauMessage3Transformer implements ResponseDefinitionTransformerV2 {

    private static final ConcurrentHashMap<String, VauServerStateMachine> vau2ServersMap = new ConcurrentHashMap<>();

    private final String name;
    private VauResponseReader vauResponseReader;

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

    public void registerVauFacade(VauFacade vauFacade) {
        vauResponseReader = new VauResponseReader(vauFacade);
    }

    public void registerVauChannel(String path, VauServerStateMachine vauServer) {
        vau2ServersMap.put(path, vauServer);
    }

    @Override
    public ResponseDefinition transform(ServeEvent serveEvent) {
        LoggedRequest request = serveEvent.getRequest();
        VauServerStateMachine vauServer = vau2ServersMap.get(request.getUrl());

        byte[] message3 = serveEvent.getRequest().getBody();
        try {
            new CBORMapper().readTree(message3);

            byte[] message4 = vauServer.receiveMessage(message3);
            return new ResponseDefinitionBuilder()
                .withHeader(VAU_DEBUG_SK2_S2C_INFO, "")
                .withHeader(VAU_DEBUG_SK2_C2S_INFO, "")
                .withHeader(CONTENT_TYPE, "application/cbor")
                .withHeader(CONTENT_LENGTH, String.valueOf(message4.length))
                .withStatus(200)
                .withBody(message4)
                .build();
        } catch (Exception e) {
            HttpParcel httpRequest = HttpParcel.from(vauServer.decryptVauMessage(message3));
            String path = httpRequest.getPath();
            return switch (path) {
                case "/epa/authz/v1/getNonce" -> prepareVauResponse(vauServer, getFixture("GetNonce.json"));
                case "/epa/authz/v1/send_authorization_request_sc" -> prepareVauResponse(
                    vauServer,
                    null,
                    "https://idp-ref.zentral.idp.splitdns.ti-dienste.de/auth?response_type=code&scope=openid+ePA-bmt-rt&nonce=GsUcyAvdAhvlorTpFyBzurZiYSaJjCdCnhB9Rgd7L4RxafFyxMDlYwHuhdR5JqE7&client_id=GEMBITMAePAe2zrxzLOR&redirect_uri=https%3A%2F%2Fe4a-rt.deine-epa.de%2F&code_challenge=CCshxJ-_K29-X3VjUfOfw2N670igmawapHepPmEfXTM&code_challenge_method=S256&state=v8XOGFO35IDqvGwS5ciaA5TVjioklDDqFrC9JUYvGejRw5i4z1dU1GRwd77rqP8y"
                );
                case "/epa/authz/v1/send_authcode_sc" -> prepareVauResponse(vauServer, getFixture("SendAuthCodeSC.json"));
                default -> throw new IllegalArgumentException("Unknown path: " + path);
            };
        }
    }

    private ResponseDefinition prepareVauResponse(VauServerStateMachine vauServer, String payload) {
        return prepareVauResponse(vauServer, payload, null);
    }

    private ResponseDefinition prepareVauResponse(VauServerStateMachine vauServer, String payload, String location) {
        List<Pair<String, String>> headers = new ArrayList<>();
        byte[] bytes = null;
        if (payload != null && !payload.isEmpty()) {
            bytes = payload.getBytes(UTF_8);
            headers.add(Pair.of(CONTENT_TYPE, "application/json"));
            headers.add(Pair.of(CONTENT_LENGTH, String.valueOf(bytes.length)));
        }
        if (location != null) {
            headers.add(Pair.of(LOCATION, location));
        }
        HttpParcel httpParcel = new HttpParcel("HTTP/1.1 200 OK", headers, bytes);
        byte[] innerResponse = httpParcel.toBytes();
        byte[] vauMessage = vauServer.encryptVauMessage(innerResponse);

        return new ResponseDefinitionBuilder()
            .withHeader(CONTENT_TYPE, "application/cbor")
            .withHeader(CONTENT_LENGTH, String.valueOf(vauMessage.length))
            .withStatus(200)
            .withBody(vauMessage).build();
    }

    private String getFixture(String fileName) {
        try {
            return Files.readString(getResourcePath(FIXTURES, fileName));
        } catch (Exception e) {
            return null;
        }
    }
}
