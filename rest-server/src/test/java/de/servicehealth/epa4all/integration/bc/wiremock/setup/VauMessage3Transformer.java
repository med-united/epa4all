package de.servicehealth.epa4all.integration.bc.wiremock.setup;

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static de.servicehealth.epa4all.common.TestUtils.getTextFixture;
import static de.servicehealth.utils.ServerUtils.APPLICATION_CBOR;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK2_C2S_INFO;
import static de.servicehealth.vau.VauClient.VAU_DEBUG_SK2_S2C_INFO;
import static de.servicehealth.vau.VauClient.VAU_ERROR;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.HttpHeaders.LOCATION;
import static java.nio.charset.StandardCharsets.UTF_8;

@Getter
public class VauMessage3Transformer implements ResponseDefinitionTransformerV2 {

    private static final ConcurrentHashMap<String, VauServerStateMachine> vau2ServersMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Function<VauServerStateMachine, ResponseDefinition>> responseMap = new ConcurrentHashMap<>();

    private final String name;
    private VauResponseReader vauResponseReader;

    public VauMessage3Transformer(String name) throws Exception {
        this.name = name;

        registerResponseFunc("/epa/authz/v1/getNonce", new CallInfo().withJsonPayload(getTextFixture("GetNonce.json")));
        registerResponseFunc(
            "/epa/authz/v1/send_authorization_request_sc",
            new CallInfo()
                .withStatus(200)
                .withInnerHeaders(List.of(Pair.of(
                    LOCATION, "https://idp-ref.zentral.idp.splitdns.ti-dienste.de/auth?response_type=code&scope=openid+ePA-bmt-rt&nonce=GsUcyAvdAhvlorTpFyBzurZiYSaJjCdCnhB9Rgd7L4RxafFyxMDlYwHuhdR5JqE7&client_id=GEMBITMAePAe2zrxzLOR&redirect_uri=https%3A%2F%2Fe4a-rt.deine-epa.de%2F&code_challenge=CCshxJ-_K29-X3VjUfOfw2N670igmawapHepPmEfXTM&code_challenge_method=S256&state=v8XOGFO35IDqvGwS5ciaA5TVjioklDDqFrC9JUYvGejRw5i4z1dU1GRwd77rqP8y"
                )))
        );
        registerResponseFunc("/epa/authz/v1/send_authcode_sc", new CallInfo().withJsonPayload(getTextFixture("SendAuthCodeSC.json")));
        registerResponseFunc("/epa/basic/api/v1/ps/entitlements", new CallInfo().withJsonPayload("{\"validTo\":\"2027-02-15T22:59:59Z\"}".getBytes(UTF_8)));
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

    public void registerResponseFunc(String path, CallInfo callInfo) {
        responseMap.put(path, vauServer -> prepareVauResponse(vauServer, callInfo));
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
                .withHeader(CONTENT_TYPE, APPLICATION_CBOR)
                .withHeader(CONTENT_LENGTH, String.valueOf(message4.length))
                .withStatus(200)
                .withBody(message4)
                .build();
        } catch (Exception e) {
            HttpParcel httpRequest = HttpParcel.from(vauServer.decryptVauMessage(message3));
            String path = httpRequest.getPath();

            return responseMap.get(path).apply(vauServer);
        }
    }

    private ResponseDefinition prepareVauResponse(VauServerStateMachine vauServer, CallInfo callInfo) {
        List<Pair<String, String>> headers = new ArrayList<>();
        byte[] bytes = callInfo.payload;
        if (bytes != null && bytes.length > 0) {
            headers.add(Pair.of(CONTENT_TYPE, callInfo.contentType));
            headers.add(Pair.of(CONTENT_LENGTH, String.valueOf(bytes.length)));
        }
        headers.addAll(callInfo.innerHeaders);
        String statusLine = String.format("HTTP/1.1 %d OK", callInfo.status); // todo status name
        HttpParcel httpParcel = new HttpParcel(statusLine, headers, bytes);
        byte[] innerResponse = httpParcel.toBytes();
        byte[] vauMessage = vauServer.encryptVauMessage(innerResponse);

        ResponseDefinitionBuilder builder = new ResponseDefinitionBuilder()
            .withHeader(CONTENT_TYPE, APPLICATION_CBOR)
            .withHeader(CONTENT_LENGTH, String.valueOf(vauMessage.length));
        if (callInfo.errorHeader != null) {
            builder.withHeader(VAU_ERROR, callInfo.errorHeader);
        }
        builder.withStatus(callInfo.status).withBody(vauMessage);
        return builder.build();
    }
}