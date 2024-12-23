package de.servicehealth.epa4all.integration.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.gematik.vau.lib.VauServerStateMachine;
import de.gematik.vau.lib.data.EccKyberKeyPair;
import de.gematik.vau.lib.data.SignedPublicVauKeys;
import de.gematik.vau.lib.data.VauPublicKeys;
import de.service.health.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.common.WireMockProfile;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpFile;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpKey;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.serviceport.ServicePortProvider;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.common.ResourceUtil.getResource;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static de.servicehealth.epa4all.common.TestUtils.getResourcePath;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
@TestProfile(WireMockProfile.class)
@QuarkusTestResource(value = WiremockTestResource.class, restrictToAnnotatedClass = true)
public class RequestVauNpIT {

    public final static String WIREMOCK = "wiremock/";
    public final static String FIXTURES = WIREMOCK + "fixtures";
    public final static String VAU = WIREMOCK + "vau";


    private static final String VAU_MESSAGE1_TRANSFORMER = "vauMessage1Transformer";
    private static final String VAU_MESSAGE2_TRANSFORMER = "vauMessage2Transformer";

    private static WireMockServer wiremock;
    private static VauMessage1Transformer vauMessage1Transformer;
    private static VauMessage2Transformer vauMessage2Transformer;

    @Inject
    IdpClient idpClient;

    @Inject
    ClientFactory clientFactory;

    @Inject
    VauNpProvider vauNpProvider;

    @Inject
    EpaMultiService epaMultiService;

    @Inject
    ServicePortProvider servicePortProvider;

    @BeforeAll
    public static void beforeAll() {
        vauMessage1Transformer = new VauMessage1Transformer(VAU_MESSAGE1_TRANSFORMER);
        vauMessage2Transformer = new VauMessage2Transformer(VAU_MESSAGE2_TRANSFORMER);
        wiremock = new WireMockServer(
            options()
                .httpsPort(9443)
                .httpDisabled(true)
                .gzipDisabled(false)
                .needClientAuth(false)
                .keystorePath(getResource(WireMockConfiguration.class, "keystore").toString())
                .keystorePassword("password")
                .extensions(
                    vauMessage1Transformer,
                    vauMessage2Transformer
                )
        );
        wiremock.start();
    }

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost:9443";
    }

    @Test
    void vauNpProvisioningReloaded() throws Exception {
        File configFolder = getResourcePath("wiremock").toFile();

        idpClient.onStart();
        clientFactory.onStart();
        epaMultiService.onStart();
        servicePortProvider.onStart();

        String soapGetCardsEnvelop = getFixture("GetCards.xml");
        wiremock.addStubMapping(post(urlEqualTo("/konnektor/ws/EventService"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody(soapGetCardsEnvelop)).build());

        epaMultiService.getEpaBackendMap().forEach((backend, epaApi) -> {
            epaApi.getVauFacade().getVauClients().forEach(vc -> {
                try {
                    // epa1/234234234/VAU
                    // epa1/987987987/VAU
                    String vauHashCode = String.valueOf(Math.abs(vc.hashCode()));
                    String path = "/" + backend.split("localhost:9443/")[1] + "/" + vauHashCode + "/VAU";

                    String key = "key";
                    Object value = new Object();

                    // 18b2333686c61604944f1d4e90a6f75d8395b79375266d561e2284756d436b4a/5920fd7c6ce64f65bab7025e1bb6f62d
                    String uniquePath = concatUuids(2) + "/" + concatUuids(1);

                    VauServerStateMachine vauServer = prepareVauServer();
                    vauMessage1Transformer.registerVauChannel(path, uniquePath, vauServer);

                    wiremock
                        .addStubMapping(post(urlEqualTo(path))
                            .willReturn(WireMock.aResponse().withTransformer(VAU_MESSAGE1_TRANSFORMER, key, value)).build());

                    path = "/VAU/" + uniquePath;
                    vauMessage2Transformer.registerVauChannel(path, vauServer);
                    wiremock
                        .addStubMapping(post(urlEqualTo(path))
                            .willReturn(WireMock.aResponse().withTransformer(VAU_MESSAGE2_TRANSFORMER, key, value)).build());

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        });

        String soapSmcbCertificateEnvelop = getFixture("SmcbCertificate.xml");
        wiremock.addStubMapping(WireMock.get(urlEqualTo("/konnektor/ws/CertificateService"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody(soapSmcbCertificateEnvelop)).build());

        String discoveryDocument = "eyJhbGciOiJCUDI1NlIxIiwia2lkIjoicHVrX2Rpc2Nfc2lnIiwieDVjIjpbIk1JSUMrakNDQXFDZ0F3SUJBZ0lDRzNvd0NnWUlLb1pJemowRUF3SXdnWVF4Q3pBSkJnTlZCQVlUQWtSRk1SOHdIUVlEVlFRS0RCWm5aVzFoZEdscklFZHRZa2dnVGs5VUxWWkJURWxFTVRJd01BWURWUVFMRENsTGIyMXdiMjVsYm5SbGJpMURRU0JrWlhJZ1ZHVnNaVzFoZEdscmFXNW1jbUZ6ZEhKMWEzUjFjakVnTUI0R0ExVUVBd3dYUjBWTkxrdFBUVkF0UTBFeU9DQlVSVk5VTFU5T1RGa3dIaGNOTWpFd05UQTJNVFV5TnpNMVdoY05Nall3TlRBMU1UVXlOek0wV2pCOU1Rc3dDUVlEVlFRR0V3SkJWREVvTUNZR0ExVUVDZ3dmVWtsVFJTQkhiV0pJSUZSRlUxUXRUMDVNV1NBdElFNVBWQzFXUVV4SlJERXBNQ2NHQTFVRUJSTWdNemczTnpndFZqQXhTVEF3TURGVU1qQXlNVEExTURZeE5ETTVOVGswTkRZeEdUQVhCZ05WQkFNTUVHUnBjMk11Y25VdWFXUndMbkpwYzJVd1dqQVVCZ2NxaGtqT1BRSUJCZ2tySkFNREFnZ0JBUWNEUWdBRWxvM1NiUTJjcmhpTlJmMC93K1FvUFE0cTY1MFNKdVM3WTJYYmxXZnFmRjRlQm96TUJBa0JjRlA1SEdaM3h1SlFJWTJJLzBTNitKVzRCbzlrek9GV3lhT0NBUVV3Z2dFQk1CMEdBMVVkRGdRV0JCUitlek1ZVDRBTGU2Wi9pS0tTNm40SXJEZDhrREFmQmdOVkhTTUVHREFXZ0JRQWFqaVE4NW11SVk5UzJ1N0JqRzZBcldFaXlUQlBCZ2dyQmdFRkJRY0JBUVJETUVFd1B3WUlLd1lCQlFVSE1BR0dNMmgwZEhBNkx5OXZZM053TWkxMFpYTjBjbVZtTG10dmJYQXRZMkV1ZEdWc1pXMWhkR2xyTFhSbGMzUXZiMk56Y0M5bFl6QU9CZ05WSFE4QkFmOEVCQU1DQjRBd0lRWURWUjBnQkJvd0dEQUtCZ2dxZ2hRQVRBU0JJekFLQmdncWdoUUFUQVNCU3pBTUJnTlZIUk1CQWY4RUFqQUFNQzBHQlNza0NBTURCQ1F3SWpBZ01CNHdIREFhTUF3TUNrbEVVQzFFYVdWdWMzUXdDZ1lJS29JVUFFd0VnZ1F3Q2dZSUtvWkl6ajBFQXdJRFNBQXdSUUlnVkozTW1BTnlkWmVCSEFzaHpsWmVUeXowSUlaajNCLzROTzJaR2JqQXZOY0NJUUNUc2FyY2lrRmJSK2dkU0dON2pzd1EydmZqR3JXeVhVVVR4R1lnQ1ZJNFFnPT0iXSwidHlwIjoiSldUIn0.eyJpYXQiOjE3MzQ3NzQ3NjEsImV4cCI6MTczNDg2MTE2MSwiaXNzdWVyIjoiaHR0cHM6Ly9pZHAtcmVmLnplbnRyYWwuaWRwLnNwbGl0ZG5zLnRpLWRpZW5zdGUuZGUiLCJqd2tzX3VyaSI6Imh0dHBzOi8vaWRwLXJlZi56ZW50cmFsLmlkcC5zcGxpdGRucy50aS1kaWVuc3RlLmRlL2NlcnRzIiwidXJpX2Rpc2MiOiJodHRwczovL2lkcC1yZWYuemVudHJhbC5pZHAuc3BsaXRkbnMudGktZGllbnN0ZS5kZS8ud2VsbC1rbm93bi9vcGVuaWQtY29uZmlndXJhdGlvbiIsImF1dGhvcml6YXRpb25fZW5kcG9pbnQiOiJodHRwczovL2lkcC1yZWYuemVudHJhbC5pZHAuc3BsaXRkbnMudGktZGllbnN0ZS5kZS9hdXRoIiwic3NvX2VuZHBvaW50IjoiaHR0cHM6Ly9pZHAtcmVmLnplbnRyYWwuaWRwLnNwbGl0ZG5zLnRpLWRpZW5zdGUuZGUvYXV0aC9zc29fcmVzcG9uc2UiLCJ0b2tlbl9lbmRwb2ludCI6Imh0dHBzOi8vaWRwLXJlZi56ZW50cmFsLmlkcC5zcGxpdGRucy50aS1kaWVuc3RlLmRlL3Rva2VuIiwidXJpX3B1a19pZHBfZW5jIjoiaHR0cHM6Ly9pZHAtcmVmLnplbnRyYWwuaWRwLnNwbGl0ZG5zLnRpLWRpZW5zdGUuZGUvY2VydHMvcHVrX2lkcF9lbmMiLCJ1cmlfcHVrX2lkcF9zaWciOiJodHRwczovL2lkcC1yZWYuemVudHJhbC5pZHAuc3BsaXRkbnMudGktZGllbnN0ZS5kZS9jZXJ0cy9wdWtfaWRwX3NpZyIsImNvZGVfY2hhbGxlbmdlX21ldGhvZHNfc3VwcG9ydGVkIjpbIlMyNTYiXSwicmVzcG9uc2VfdHlwZXNfc3VwcG9ydGVkIjpbImNvZGUiXSwiZ3JhbnRfdHlwZXNfc3VwcG9ydGVkIjpbImF1dGhvcml6YXRpb25fY29kZSJdLCJpZF90b2tlbl9zaWduaW5nX2FsZ192YWx1ZXNfc3VwcG9ydGVkIjpbIkJQMjU2UjEiXSwiYWNyX3ZhbHVlc19zdXBwb3J0ZWQiOlsiZ2VtYXRpay1laGVhbHRoLWxvYS1oaWdoIl0sInJlc3BvbnNlX21vZGVzX3N1cHBvcnRlZCI6WyJxdWVyeSJdLCJ0b2tlbl9lbmRwb2ludF9hdXRoX21ldGhvZHNfc3VwcG9ydGVkIjpbIm5vbmUiXSwic2NvcGVzX3N1cHBvcnRlZCI6WyJvcGVuaWQiLCJlLXJlemVwdCIsImUtcmV6ZXB0LWRldiIsImVQQS1QUy1nZW10ayIsImVQQS1ibXQtcXQiLCJlUEEtYm10LXJ0IiwiZVBBLWlibTEiLCJlUEEtaWJtMiIsImVidG0tYmRyIiwiZWJ0bS1iZHIyIiwiZmgtZm9rdXMtZGVtaXMiLCJmaGlyLXZ6ZCIsImdlbS1hdXRoIiwiZ210aWstZGVtaXMiLCJnbXRpay1kZW1pcy1ma2IiLCJnbXRpay1kZW1pcy1mcmEiLCJnbXRpay1kZW1pcy1xcyIsImdtdGlrLWRlbWlzLXJlZiIsImdtdGlrLWZoaXJkaXJlY3Rvcnktc3NwIiwiZ210aWstemVyb3RydXN0LXBvYyIsImlyZC1ibWciLCJrdnNoLW9wdCIsIm9nci1uZXhlbmlvLWRlbW8iLCJvZ3ItbmV4ZW5pby1kZXYiLCJvZ3ItbmV4ZW5pby1wcmVwcm9kIiwib2dyLW5leGVuaW8tdGVzdCIsIm9yZ2Fuc3BlbmRlLXJlZ2lzdGVyIiwicGFpcmluZyIsInJwZG9jLWVtbWEiLCJycGRvYy1lbW1hLXBoYWIiLCJ0aS1tZXNzZW5nZXIiLCJ0aS1zY29yZSIsInRpLXNjb3JlMiIsInp2ci1ibm90ayJdLCJzdWJqZWN0X3R5cGVzX3N1cHBvcnRlZCI6WyJwYWlyd2lzZSJdfQ.VyIGl5GNG0o4CQnwHNjkepf_FRbQOXhUJ3YZHb35WhWHPXez2fX9YnVx4hNnDF3U3Nvi8vmS8iD85UHGof9SYA";
        wiremock.addStubMapping(WireMock.get(urlEqualTo("/idp"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody(discoveryDocument)).build());

        vauNpProvider.reload(false);

        Map<VauNpKey, String> map = new VauNpFile(configFolder).get();
        assertFalse(map.isEmpty());
    }

    private String getFixture(String fileName) throws Exception {
        return Files.readString(getResourcePath(FIXTURES, fileName));
    }

    private String concatUuids(int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(UUID.randomUUID().toString().replace("-", ""));
        }
        return sb.toString();
    }

    private VauServerStateMachine prepareVauServer() throws Exception {
        Files.deleteIfExists(Path.of("target/vau3traffic.tgr"));

        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(Files.readAllBytes(getResourcePath(VAU, "vau-sig-key.der")));
        PrivateKey serverAutPrivateKey = keyFactory.generatePrivate(privateSpec);

        final EccKyberKeyPair serverVauKeyPair = EccKyberKeyPair.readFromFile(getResourcePath(VAU, "vau_server_keys.cbor"));
        final VauPublicKeys serverVauKeys = new VauPublicKeys(serverVauKeyPair, "VAU Server Keys", Duration.ofDays(30));
        var signedPublicVauKeys = SignedPublicVauKeys.sign(
            Files.readAllBytes(getResourcePath(VAU, "vau_sig_cert.der")), serverAutPrivateKey,
            Files.readAllBytes(getResourcePath(VAU, "ocsp-response-vau-sig.der")),
            1,
            serverVauKeys
        );

        return new VauServerStateMachine(signedPublicVauKeys, serverVauKeyPair);
    }
}

