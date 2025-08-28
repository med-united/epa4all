package de.servicehealth.epa4all.integration.base;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.gematik.vau.lib.VauServerStateMachine;
import de.gematik.vau.lib.data.EccKyberKeyPair;
import de.gematik.vau.lib.data.SignedPublicVauKeys;
import de.gematik.vau.lib.data.VauPublicKeys;
import de.gematik.ws.conn.eventservice.v7.Event;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.KonnektorsConfigs;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.cetp.domain.eventservice.event.DecodeResult;
import de.health.service.cetp.domain.fault.CetpFault;
import de.servicehealth.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.integration.bc.wiremock.setup.CallInfo;
import de.servicehealth.epa4all.integration.bc.wiremock.setup.VauMessage1Transformer;
import de.servicehealth.epa4all.integration.bc.wiremock.setup.VauMessage3Transformer;
import de.servicehealth.epa4all.server.FeatureConfig;
import de.servicehealth.epa4all.server.cetp.CETPEventHandler;
import de.servicehealth.epa4all.server.cetp.mapper.event.EventMapper;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.jcr.JcrService;
import de.servicehealth.epa4all.server.rest.consent.ConsentFunction;
import de.servicehealth.epa4all.server.serviceport.ServicePortProvider;
import de.servicehealth.epa4all.server.vsd.VsdService;
import de.servicehealth.folder.WebdavConfig;
import de.servicehealth.model.ConsentDecisionsResponseType;
import de.servicehealth.model.GetConsentDecisionInformation200Response;
import de.servicehealth.registry.BeanRegistry;
import de.servicehealth.vau.VauFacade;
import io.netty.channel.embedded.EmbeddedChannel;
import io.quarkus.test.junit.QuarkusMock;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.common.ResourceUtil.getResource;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static de.servicehealth.epa4all.common.TestUtils.WIREMOCK;
import static de.servicehealth.epa4all.common.TestUtils.deleteFiles;
import static de.servicehealth.epa4all.common.TestUtils.getResourcePath;
import static de.servicehealth.epa4all.common.TestUtils.getTextFixture;
import static de.servicehealth.utils.ServerUtils.makeSimplePath;
import static jakarta.ws.rs.core.HttpHeaders.LOCATION;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static wiremock.com.google.common.net.HttpHeaders.CONTENT_TYPE;

public abstract class AbstractWiremockTest extends AbstractWebdavIT {

    public final static String VAU = WIREMOCK + "vau";

    protected static final String KEY = "key";
    protected static final String VALUE = "value";

    protected static final String VAU_MESSAGE1_TRANSFORMER = "vauMessage1Transformer";
    protected static final String VAU_MESSAGE3_TRANSFORMER = "vauMessage3Transformer";

    protected static WireMockServer wiremock;
    protected static VauMessage1Transformer vauMessage1Transformer;
    protected static VauMessage3Transformer vauMessage3Transformer;

    @Inject
    IdpClient idpClient;

    @Inject
    JcrService jcrService;

    @Inject
    VsdService vsdService;

    @Inject
    ClientFactory clientFactory;

    @Inject
    protected BeanRegistry registry;

    @Inject
    protected EventMapper eventMapper;

    @Inject
    protected WebdavConfig webdavConfig;

    @Inject
    protected FolderService folderService;

    @Inject
    protected VauNpProvider vauNpProvider;

    @Inject
    ServicePortProvider servicePortProvider;

    @Inject
    protected EpaMultiService epaMultiService;

    @Inject
    protected IKonnektorClient konnektorClient;

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    protected DefaultUserConfig defaultUserConfig;

    @Inject
    CETPEventHandlerProvider eventHandlerProvider;

    @Inject
    @KonnektorsConfigs
    Map<String, KonnektorConfig> konnektorConfigs;

    @Inject
    protected KonnektorDefaultConfig konnektorDefaultConfig;

    protected static Path tempDir;

    protected Gson gson = new GsonBuilder().create();

    @BeforeAll
    public static void beforeAll() throws Exception {
        tempDir = Files.createTempDirectory(UUID.randomUUID().toString());

        System.setProperty("javax.xml.accessExternalDTD", "all");
        System.setProperty("javax.xml.transform.TransformerFactory", "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");

        vauMessage1Transformer = new VauMessage1Transformer(VAU_MESSAGE1_TRANSFORMER);
        vauMessage3Transformer = new VauMessage3Transformer(VAU_MESSAGE3_TRANSFORMER);
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
                    vauMessage3Transformer
                )
        );
        wiremock.start();
    }

    @AfterAll
    static void afterAll() {
        if (wiremock != null) {
            wiremock.stop();
        }
        tempDir.toFile().delete();
    }

    @BeforeEach
    public void beforeEach() throws Exception {
        clientFactory.doStart();
        prepareIdpStubs();
        prepareKonnektorStubs();
        mockWebdavConfig(tempDir.toFile(), null, null);
    }

    @AfterEach
    public void afterEach() {
        jcrService.shutdown();
        registry.getInstances(VauFacade.class).forEach(registry::unregister);
        deleteFiles(tempDir.toFile().listFiles());
        epaMultiService.getEpaBackendMap().values().forEach(epaAPI -> {
            epaAPI.getVauFacade().getSessionClients().forEach(vc -> vc.forceRelease(null));
        });
        QuarkusMock.installMockForType(webdavConfig, WebdavConfig.class);
        QuarkusMock.installMockForType(folderService, FolderService.class);
    }

    protected UCPersoenlicheVersichertendatenXML.Versicherter.Person prepareInsurantFiles(
        String telematikId,
        String kvnr
    ) throws Exception {
        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, defaultUserConfig.getUserConfigurations());
        String egkHandle = konnektorClient.getEgkHandle(runtimeConfig, kvnr);
        String smcbHandle = konnektorClient.getSmcbHandle(runtimeConfig);

        String insurantId = vsdService.read(egkHandle, smcbHandle, runtimeConfig, telematikId, null);
        InsuranceData insuranceData = insuranceDataService.getData(telematikId, insurantId);
        UCPersoenlicheVersichertendatenXML versichertendaten = insuranceData.getPersoenlicheVersichertendaten();
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person = versichertendaten.getVersicherter().getPerson();
        assertNotNull(person);
        return person;
    }

    protected void prepareVauStubs(List<Pair<String, CallInfo>> responseFuncs) throws Exception {
        epaMultiService.doStart();
        epaMultiService.getEpaBackendMap().forEach((backend, epaApi) ->
            epaApi.getVauFacade().getEmptyClients().forEach(vc -> {
                try {
                    // /234234234/VAU
                    // /987987987/VAU
                    String vauHashCode = String.valueOf(Math.abs(vc.hashCode()));
                    String vauPath = "/" + vauHashCode + "/VAU";

                    String uniquePath = concatUuids(2) + "/" + concatUuids(1);

                    VauServerStateMachine vauServer = prepareVauServer();

                    vauMessage1Transformer.registerVauChannel(vauPath, uniquePath, vauServer);

                    wiremock
                        .addStubMapping(post(urlEqualTo(vauPath))
                            .willReturn(WireMock.aResponse().withTransformer(VAU_MESSAGE1_TRANSFORMER, KEY, VALUE)).build());

                    vauPath = vauPath + "/" + uniquePath;

                    vauMessage3Transformer.registerVauChannel(vauPath, vauServer);
                    vauMessage3Transformer.registerVauFacade(epaApi.getVauFacade());
                    wiremock
                        .addStubMapping(post(urlEqualTo(vauPath))
                            .willReturn(WireMock.aResponse().withTransformer(VAU_MESSAGE3_TRANSFORMER, KEY, VALUE)).build());

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        responseFuncs.forEach(p -> vauMessage3Transformer.registerResponseFunc(p.getKey(), p.getValue()));

        vauNpProvider.doStart();
        jcrService.doStart();
    }

    protected void prepareInformationStubs(int status) {
        wiremock.addStubMapping(get(urlEqualTo("/information/api/v1/ehr"))
            .willReturn(WireMock.aResponse().withStatus(status)).build());
    }

    protected void prepareConsentStubs(Map<ConsentFunction, String> functions) {
        List<ConsentDecisionsResponseType> decisionsResponseTypes = new ArrayList<>();
        for (Map.Entry<ConsentFunction, String> e : functions.entrySet()) {
            ConsentDecisionsResponseType responseType = new ConsentDecisionsResponseType();
            responseType.functionId(e.getKey().getFunction());
            responseType.decision(e.getValue());
            decisionsResponseTypes.add(responseType);
        }
        GetConsentDecisionInformation200Response response = new GetConsentDecisionInformation200Response();
        response.data(decisionsResponseTypes);
        wiremock.addStubMapping(get(urlEqualTo("/information/api/v1/ehr/consentdecisions"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .withBody(gson.toJson(response))
            ).build());
    }

    protected void prepareKonnektorStubs() throws Exception {
        servicePortProvider.doStart();

        byte[] soapGetHbaCardsEnvelop = getTextFixture("GetHbaCards.xml");
        wiremock.addStubMapping(post(urlEqualTo("/konnektor/ws/EventService")).withRequestBody(containing("HBA"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody(soapGetHbaCardsEnvelop)).build());

        byte[] soapGetSmcbCardsEnvelop = getTextFixture("GetSmcbCards.xml");
        wiremock.addStubMapping(post(urlEqualTo("/konnektor/ws/EventService")).withRequestBody(containing("SMC-B"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody(soapGetSmcbCardsEnvelop)).build());

        byte[] soapGetEgkCardsEnvelop = getTextFixture("GetEgkCards.xml");
        wiremock.addStubMapping(post(urlEqualTo("/konnektor/ws/EventService")).withRequestBody(containing("EGK"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody(soapGetEgkCardsEnvelop)).build());

        byte[] soapHbaCertificateEnvelop = getTextFixture("HbaCertificate.xml");
        wiremock.addStubMapping(post(urlEqualTo("/konnektor/ws/CertificateService")).withRequestBody(containing("C.QES"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody(soapHbaCertificateEnvelop)).build());

        byte[] soapSmcbCertificateEnvelop = getTextFixture("SmcbCertificate.xml");
        wiremock.addStubMapping(post(urlEqualTo("/konnektor/ws/CertificateService")).withRequestBody(containing("C.AUT"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody(soapSmcbCertificateEnvelop)).build());

        byte[] soapExternalAuthenticateEnvelop = getTextFixture("ExternalAuthenticateResponse.xml");
        wiremock.addStubMapping(post(urlEqualTo("/konnektor/ws/AuthSignatureService"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody(soapExternalAuthenticateEnvelop)).build());

        byte[] soapReadVSDResponseEnvelop = getTextFixture("ReadVSDResponseSample.xml");
        wiremock.addStubMapping(post(urlEqualTo("/konnektor/ws/VSDService"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody(soapReadVSDResponseEnvelop)).build());
    }

    protected void prepareIdpStubs() throws Exception {
        // String discoveryDocument = "eyJhbGciOiJCUDI1NlIxIiwia2lkIjoicHVrX2Rpc2Nfc2lnIiwieDVjIjpbIk1JSUMrakNDQXFDZ0F3SUJBZ0lDRzNvd0NnWUlLb1pJemowRUF3SXdnWVF4Q3pBSkJnTlZCQVlUQWtSRk1SOHdIUVlEVlFRS0RCWm5aVzFoZEdscklFZHRZa2dnVGs5VUxWWkJURWxFTVRJd01BWURWUVFMRENsTGIyMXdiMjVsYm5SbGJpMURRU0JrWlhJZ1ZHVnNaVzFoZEdscmFXNW1jbUZ6ZEhKMWEzUjFjakVnTUI0R0ExVUVBd3dYUjBWTkxrdFBUVkF0UTBFeU9DQlVSVk5VTFU5T1RGa3dIaGNOTWpFd05UQTJNVFV5TnpNMVdoY05Nall3TlRBMU1UVXlOek0wV2pCOU1Rc3dDUVlEVlFRR0V3SkJWREVvTUNZR0ExVUVDZ3dmVWtsVFJTQkhiV0pJSUZSRlUxUXRUMDVNV1NBdElFNVBWQzFXUVV4SlJERXBNQ2NHQTFVRUJSTWdNemczTnpndFZqQXhTVEF3TURGVU1qQXlNVEExTURZeE5ETTVOVGswTkRZeEdUQVhCZ05WQkFNTUVHUnBjMk11Y25VdWFXUndMbkpwYzJVd1dqQVVCZ2NxaGtqT1BRSUJCZ2tySkFNREFnZ0JBUWNEUWdBRWxvM1NiUTJjcmhpTlJmMC93K1FvUFE0cTY1MFNKdVM3WTJYYmxXZnFmRjRlQm96TUJBa0JjRlA1SEdaM3h1SlFJWTJJLzBTNitKVzRCbzlrek9GV3lhT0NBUVV3Z2dFQk1CMEdBMVVkRGdRV0JCUitlek1ZVDRBTGU2Wi9pS0tTNm40SXJEZDhrREFmQmdOVkhTTUVHREFXZ0JRQWFqaVE4NW11SVk5UzJ1N0JqRzZBcldFaXlUQlBCZ2dyQmdFRkJRY0JBUVJETUVFd1B3WUlLd1lCQlFVSE1BR0dNMmgwZEhBNkx5OXZZM053TWkxMFpYTjBjbVZtTG10dmJYQXRZMkV1ZEdWc1pXMWhkR2xyTFhSbGMzUXZiMk56Y0M5bFl6QU9CZ05WSFE4QkFmOEVCQU1DQjRBd0lRWURWUjBnQkJvd0dEQUtCZ2dxZ2hRQVRBU0JJekFLQmdncWdoUUFUQVNCU3pBTUJnTlZIUk1CQWY4RUFqQUFNQzBHQlNza0NBTURCQ1F3SWpBZ01CNHdIREFhTUF3TUNrbEVVQzFFYVdWdWMzUXdDZ1lJS29JVUFFd0VnZ1F3Q2dZSUtvWkl6ajBFQXdJRFNBQXdSUUlnVkozTW1BTnlkWmVCSEFzaHpsWmVUeXowSUlaajNCLzROTzJaR2JqQXZOY0NJUUNUc2FyY2lrRmJSK2dkU0dON2pzd1EydmZqR3JXeVhVVVR4R1lnQ1ZJNFFnPT0iXSwidHlwIjoiSldUIn0.eyJpYXQiOjE3MzQ3NzQ3NjEsImV4cCI6MTczNDg2MTE2MSwiaXNzdWVyIjoiaHR0cHM6Ly9pZHAtcmVmLnplbnRyYWwuaWRwLnNwbGl0ZG5zLnRpLWRpZW5zdGUuZGUiLCJqd2tzX3VyaSI6Imh0dHBzOi8vaWRwLXJlZi56ZW50cmFsLmlkcC5zcGxpdGRucy50aS1kaWVuc3RlLmRlL2NlcnRzIiwidXJpX2Rpc2MiOiJodHRwczovL2lkcC1yZWYuemVudHJhbC5pZHAuc3BsaXRkbnMudGktZGllbnN0ZS5kZS8ud2VsbC1rbm93bi9vcGVuaWQtY29uZmlndXJhdGlvbiIsImF1dGhvcml6YXRpb25fZW5kcG9pbnQiOiJodHRwczovL2lkcC1yZWYuemVudHJhbC5pZHAuc3BsaXRkbnMudGktZGllbnN0ZS5kZS9hdXRoIiwic3NvX2VuZHBvaW50IjoiaHR0cHM6Ly9pZHAtcmVmLnplbnRyYWwuaWRwLnNwbGl0ZG5zLnRpLWRpZW5zdGUuZGUvYXV0aC9zc29fcmVzcG9uc2UiLCJ0b2tlbl9lbmRwb2ludCI6Imh0dHBzOi8vaWRwLXJlZi56ZW50cmFsLmlkcC5zcGxpdGRucy50aS1kaWVuc3RlLmRlL3Rva2VuIiwidXJpX3B1a19pZHBfZW5jIjoiaHR0cHM6Ly9pZHAtcmVmLnplbnRyYWwuaWRwLnNwbGl0ZG5zLnRpLWRpZW5zdGUuZGUvY2VydHMvcHVrX2lkcF9lbmMiLCJ1cmlfcHVrX2lkcF9zaWciOiJodHRwczovL2lkcC1yZWYuemVudHJhbC5pZHAuc3BsaXRkbnMudGktZGllbnN0ZS5kZS9jZXJ0cy9wdWtfaWRwX3NpZyIsImNvZGVfY2hhbGxlbmdlX21ldGhvZHNfc3VwcG9ydGVkIjpbIlMyNTYiXSwicmVzcG9uc2VfdHlwZXNfc3VwcG9ydGVkIjpbImNvZGUiXSwiZ3JhbnRfdHlwZXNfc3VwcG9ydGVkIjpbImF1dGhvcml6YXRpb25fY29kZSJdLCJpZF90b2tlbl9zaWduaW5nX2FsZ192YWx1ZXNfc3VwcG9ydGVkIjpbIkJQMjU2UjEiXSwiYWNyX3ZhbHVlc19zdXBwb3J0ZWQiOlsiZ2VtYXRpay1laGVhbHRoLWxvYS1oaWdoIl0sInJlc3BvbnNlX21vZGVzX3N1cHBvcnRlZCI6WyJxdWVyeSJdLCJ0b2tlbl9lbmRwb2ludF9hdXRoX21ldGhvZHNfc3VwcG9ydGVkIjpbIm5vbmUiXSwic2NvcGVzX3N1cHBvcnRlZCI6WyJvcGVuaWQiLCJlLXJlemVwdCIsImUtcmV6ZXB0LWRldiIsImVQQS1QUy1nZW10ayIsImVQQS1ibXQtcXQiLCJlUEEtYm10LXJ0IiwiZVBBLWlibTEiLCJlUEEtaWJtMiIsImVidG0tYmRyIiwiZWJ0bS1iZHIyIiwiZmgtZm9rdXMtZGVtaXMiLCJmaGlyLXZ6ZCIsImdlbS1hdXRoIiwiZ210aWstZGVtaXMiLCJnbXRpay1kZW1pcy1ma2IiLCJnbXRpay1kZW1pcy1mcmEiLCJnbXRpay1kZW1pcy1xcyIsImdtdGlrLWRlbWlzLXJlZiIsImdtdGlrLWZoaXJkaXJlY3Rvcnktc3NwIiwiZ210aWstemVyb3RydXN0LXBvYyIsImlyZC1ibWciLCJrdnNoLW9wdCIsIm9nci1uZXhlbmlvLWRlbW8iLCJvZ3ItbmV4ZW5pby1kZXYiLCJvZ3ItbmV4ZW5pby1wcmVwcm9kIiwib2dyLW5leGVuaW8tdGVzdCIsIm9yZ2Fuc3BlbmRlLXJlZ2lzdGVyIiwicGFpcmluZyIsInJwZG9jLWVtbWEiLCJycGRvYy1lbW1hLXBoYWIiLCJ0aS1tZXNzZW5nZXIiLCJ0aS1zY29yZSIsInRpLXNjb3JlMiIsInp2ci1ibm90ayJdLCJzdWJqZWN0X3R5cGVzX3N1cHBvcnRlZCI6WyJwYWlyd2lzZSJdfQ.VyIGl5GNG0o4CQnwHNjkepf_FRbQOXhUJ3YZHb35WhWHPXez2fX9YnVx4hNnDF3U3Nvi8vmS8iD85UHGof9SYA";
        // wiremock.addStubMapping(WireMock.get(urlEqualTo("/idp"))
        //     .willReturn(WireMock.aResponse().withStatus(200).withBody(discoveryDocument)).build());

        idpClient.doStart();

        byte[] jsonAuthenticationResponse = getTextFixture("AuthenticationResponse.json");
        wiremock.addStubMapping(post(urlEqualTo("/idp/auth"))
            .willReturn(WireMock.aResponse()
                .withHeader(LOCATION, "https://e4a-rt.deine-epa.de/?code\u003deyJlbmMiOiJBMjU2R0NNIiwiY3R5IjoiTkpXVCIsImV4cCI6MTczNTAwOTU5MiwiYWxnIjoiZGlyIiwia2lkIjoiMDAwMSJ9..jQOcvkSkNe_2svy6.yswP6uELSRQSBvJrewOOXjmLWwTccmhWKXXrsDPfBo6vZButt0rvkV0cosOyksnbCfQqLscWaJCF3UQZ06jDiIqB_A1OlBY6tfgVLnLfe2QRtXbmcQOl-aQSyu3QDMZ_Qc0fxrGfK4PhMrYOHwWniaptNXStr59EzeXGHVbkfasxu2ALhuS94SP0PsxMyicWiOWEZT34Tc1rS2g6YZQzrH0spsPDUES9nMnH-m-y7ZX8VDs7iVMbJ-0njR9KdvKMPjoZGicPYt54jDPiAy_5Ge_e9PxY3vpfiq2Ey7tdg4alhYhkVzPR6L6kqE3uunYSamkwuMo2VIj60S8rYol3sHmYR6ywaiZ-b9TjT_XI7LuPLeMgBlGBP7SOoCikpR0QuX6NBTPmvN1TCOpmuyrdyBHGAEhqCbqtB0Y6l5Y247DHU6ccKZi9n1L3WQ795GLBaayntvhlsNQSr667xlj0aNLe4wWjxEHDUI8o8XQRkTdXg457adL5ETFAR7_RcjVYjZW9Dk2fAo39pmOQhI8lYKdm2-epO8GLSz-T6AJrNqb2nI7dSDq2waY0NBLezQxZKXHXEoMGMsLp8NS7gtQT_zaoGYGZzlmxfZyFg-a8S6F_KIpTPYzvkzntr671Wz_EuPskyY7eDf4ziDDiN8tuo6lgEzKpwDgJDn8-6lD-8vb9gU52O9YhgsrFpbmWL8aMUOTaLE1sKIYCFZOoFfkW_zZ-gY8mjHtCZ3QUDGaVBb5a1lmdr-6k4XG4qk7IBrOjKTHVHH1sbgw76VHTFHH9v6r4ylFpB1LSY4Ce13nghSUDE_f15Xa2BfEUgFPPZhqyGVnSE_ITa1BNQ0ivQs7uacE2xKZaUxKTz-np2RIAgqERWdqAxChcoSvKAKOHipqGKyR8VVZbo5rHlN4Pt9Ng1UU651fLMaEW0Zh8R5bBBIAX-oH37VKn-m8b-7IVmlvcYfQkfT12pr1BpPConY1qLGZfsoptKfwhVDGioKz0VaGy--ksuaABNMi0DQG4BchJDORWR89TYRI-tFhA8oHVgEsq4ftdh-Awc1SNMjcGxeXAPvrudMzcy7VrPyMBacQyJ894XwZCJiWEjRfLJCRVQeUJrSuwDbnQ0VyNpwrAgEA65f8dEH_7UBvvXeCey5JgnJFkEmnfhVoh8i5cqcM9FwauzybHMHUwjxFlMbkmJTSLLKqX14tG7nMmYoThr4GxbscfDcPdvmvlTKIROaLeXhtekQR1rU_y2PMMBOwcSkbwa1N-_KxzcAjgxZr15tA65S-_w4RqP_1hUbsRc3cPoZ6WYuGqyQCoJp2jxqRdjl1TTlCut_vy8nLaJlWtfsu5Y3sBgN2qObegmTD6iodKuY6Rdbs0cMNYIXKQVmHvavfYtYwy8HOb3gXaZdCBxr9xHiUA5p1AMJoX9qdt31DQK1djOlwX-tICnShsw9_gEMhR2O3b9hRs7emCxmTh0ca2P15BMMZoNxgnpSk8MehX_eRK2ZT_zkYI8pRc74bvgMAfkH1NDm04gIutX8auk8a5SMZQy-hm7xtdwAfB6hPVVlMJ8Piv3Lcs2m2AicGTSynvw0cdAVlcCUU5pwcd-h69xIOef8yXApMp1hvzr2lQ-ZcNEoQcEGyNFefQ4nLmqjh2izO-G8SSVi-z99Jys35IqtHUMCE.SJLonvjMnBK6X9nxtOLo_Q\u0026state\u003dnRGsqMXISWqr6oaTcdGMcs6Oxt2vIOebYvUwd85BtLLk8d0vAgiy88kP3gBsr6xL")
                .withStatus(302).withBody(jsonAuthenticationResponse)).build());

        byte[] jsonAuthenticationChallenge = getTextFixture("AuthenticationChallenge.json");
        wiremock.addStubMapping(get(urlPathMatching("/idp/auth.*"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody(jsonAuthenticationChallenge)).build());
    }

    protected String concatUuids(int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(UUID.randomUUID().toString().replace("-", ""));
        }
        return sb.toString();
    }

    private VauServerStateMachine prepareVauServer() throws Exception {
        Files.deleteIfExists(Path.of(makeSimplePath("target", "vau3traffic.tgr")));

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

    protected CardlinkClient receiveCardInsertedEvent(
        EpaFileDownloader mockFileDownloader,
        FeatureConfig mockFeatureConfig,
        String kvnr
    ) throws CetpFault {
        CardlinkClient cardlinkClient = mock(CardlinkClient.class);
        CETPEventHandler cetpServerHandler = eventHandlerProvider.get(mockFileDownloader, cardlinkClient, mockFeatureConfig);
        EmbeddedChannel channel = new EmbeddedChannel(cetpServerHandler);

        String slotIdValue = "3";
        String ctIdValue = "cardTerminal-124";

        KonnektorConfig konnektorConfig = konnektorConfigs.values().iterator().next();
        String egkHandle = konnektorClient.getEgkHandle(defaultUserConfig, kvnr);
        channel.writeOneInbound(decode(konnektorConfig, slotIdValue, ctIdValue, egkHandle));
        channel.pipeline().fireChannelReadComplete();
        return cardlinkClient;
    }

    protected DecodeResult decode(
        KonnektorConfig konnektorConfig,
        String slotIdValue,
        String ctIdValue,
        String egkHandle
    ) {
        Event event = new Event();
        event.setTopic("CARD/INSERTED");
        Event.Message message = new Event.Message();
        Event.Message.Parameter parameter = new Event.Message.Parameter();
        parameter.setKey("CardHandle");
        parameter.setValue(egkHandle);
        Event.Message.Parameter parameterSlotId = new Event.Message.Parameter();
        parameterSlotId.setKey("SlotID");
        parameterSlotId.setValue(slotIdValue);
        Event.Message.Parameter parameterCtId = new Event.Message.Parameter();
        parameterCtId.setKey("CtID");
        parameterCtId.setValue(ctIdValue);
        Event.Message.Parameter parameterCardType = new Event.Message.Parameter();
        parameterCardType.setKey("CardType");
        parameterCardType.setValue("EGK");

        message.getParameter().add(parameter);
        message.getParameter().add(parameterSlotId);
        message.getParameter().add(parameterCtId);
        message.getParameter().add(parameterCardType);
        event.setMessage(message);
        return new DecodeResult(eventMapper.toDomain(event), konnektorConfig.getUserConfigurations());
    }
}