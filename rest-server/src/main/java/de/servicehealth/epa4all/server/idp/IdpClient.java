package de.servicehealth.epa4all.server.idp;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.AuthorizationRequest;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.field.ClaimName;
import de.gematik.idp.field.CodeChallengeMethod;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.MultiEpaService;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.server.idp.action.AuthAction;
import de.servicehealth.epa4all.server.idp.action.LoginAction;
import de.servicehealth.epa4all.server.idp.action.VauNpAction;
import de.servicehealth.epa4all.server.idp.func.IdpFunc;
import de.servicehealth.epa4all.server.idp.func.IdpFuncer;
import de.servicehealth.epa4all.server.serviceport.MultiKonnektorService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import kong.unirest.core.HttpResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.jwt.JwtClaims;

import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

import static de.health.service.cetp.domain.eventservice.card.CardType.SMC_B;
import static de.servicehealth.epa4all.server.idp.action.AuthAction.URN_BSI_TR_03111_ECDSA;
import static de.servicehealth.epa4all.server.idp.action.AuthAction.USER_AGENT;
import static de.servicehealth.epa4all.server.idp.utils.IdpUtils.getSignedJwt;

@ApplicationScoped
public class IdpClient {

    private final static Logger log = Logger.getLogger(IdpClient.class.getName());

    public static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();

    static {
    	Security.removeProvider(BOUNCY_CASTLE_PROVIDER.getName());
        Security.insertProviderAt(BOUNCY_CASTLE_PROVIDER, 1);
    }

    IdpFuncer idpFuncer;
    IdpConfig idpConfig;
    MultiEpaService multiEpaService;
    IKonnektorClient konnektorClient;
    AuthenticatorClient authenticatorClient;
    MultiKonnektorService multiKonnektorService;

    private DiscoveryDocumentResponse discoveryDocumentResponse;

    // A_24883-02 - clientAttest als ECDSA-Signatur
    private String signatureType = URN_BSI_TR_03111_ECDSA;

    @Inject
    public IdpClient(
        IdpFuncer idpFuncer,
        IdpConfig idpConfig,
        MultiEpaService multiEpaService,
        IKonnektorClient konnektorClient,
        AuthenticatorClient authenticatorClient,
        MultiKonnektorService multiKonnektorService
    ) {
        this.idpFuncer = idpFuncer;
        this.idpConfig = idpConfig;
        this.multiEpaService = multiEpaService;
        this.konnektorClient = konnektorClient;
        this.authenticatorClient = authenticatorClient;
        this.multiKonnektorService = multiKonnektorService;
    }

    void onStart(@Observes StartupEvent ev) {
        discoveryDocumentResponse = authenticatorClient.retrieveDiscoveryDocument(
            idpConfig.getDiscoveryDocumentUrl(), Optional.empty()
        );
    }

    public void getVauNp(UserRuntimeConfig userRuntimeConfig, Consumer<String> vauNPConsumer) throws Exception {
        IdpFunc idpFunc = idpFuncer.init("X110486750", userRuntimeConfig);
        VauNpAction authAction = new VauNpAction(
            authenticatorClient,
            discoveryDocumentResponse,
            vauNPConsumer,
            idpFunc
        );
        processVauNPAction(userRuntimeConfig, authAction, idpFunc);
    }
    
    public String getVauNpSync(UserRuntimeConfig userRuntimeConfig) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ThreadLocal<String> threadLocalString = new ThreadLocal<String>();
        IdpFunc idpFunc = idpFuncer.init("X110486750", userRuntimeConfig);
        VauNpAction authAction = new VauNpAction(
            authenticatorClient,
            discoveryDocumentResponse,
            (s) -> {
            	threadLocalString.set(s);
            	countDownLatch.countDown();
            },
            idpFunc
        );
        processVauNPAction(userRuntimeConfig, authAction, idpFunc);
        countDownLatch.await();
        return threadLocalString.get();
    }

    public void getBearerToken(UserRuntimeConfig userRuntimeConfig, Consumer<String> bearerConsumer) throws Exception {
        IdpFunc idpFunc = idpFuncer.init("X110486750", userRuntimeConfig);
        LoginAction authAction = new LoginAction(
            idpConfig.getClientId(),
            idpConfig.getAuthRequestRedirectUrl(),
            authenticatorClient,
            discoveryDocumentResponse,
            bearerConsumer,
            idpFunc
        );
        processBearerAction(userRuntimeConfig, authAction, idpFuncer);
    }

    public void processBearerAction(
        UserRuntimeConfig userRuntimeConfig,
        AuthAction authAction,
        IdpFuncer idpFuncer
    ) throws Exception {

    }

    public void processVauNPAction(
        UserRuntimeConfig userRuntimeConfig,
        AuthAction authAction,
        IdpFunc idpFunc
    ) throws Exception {
        List<Card> cards = konnektorClient.getCards(userRuntimeConfig, SMC_B);
        String smcbHandle = cards.getFirst().getCardHandle();

        // A_24881 - Nonce anfordern für Erstellung "Attestation der Umgebung"
        String nonce = idpFunc.getNonceSupplier().get();

        Pair<X509Certificate, Boolean> smcbAuthCertPair = konnektorClient.getSmcbX509Certificate(userRuntimeConfig, smcbHandle);
        if (smcbAuthCertPair.getValue()) {
            // A_24884-01 - clientAttest signieren als PKCS#1-Signatur
            signatureType = "urn:ietf:rfc:3447";
        }

        JwtClaims claims = new JwtClaims();
        claims.setClaim(ClaimName.NONCE.getJoseName(), nonce);
        claims.setClaim(ClaimName.ISSUED_AT.getJoseName(), System.currentTimeMillis() / 1000);
        claims.setClaim(ClaimName.EXPIRES_AT.getJoseName(), (System.currentTimeMillis() / 1000) + 300);

        // A_24882-01 - Signatur clientAttest
        X509Certificate smcbAuthCert = smcbAuthCertPair.getKey();
        String clientAttest = getSignedJwt(
            smcbAuthCert,
            claims,
            signatureType,
            smcbHandle,
            true,
            idpFunc
        );

        // A_24760 - Start der Nutzerauthentifizierung
        try (Response response = idpFunc.getAuthorizationResponseSupplier().get()) {
            // Parse query string into map
            Map<String, String> queryMap = new HashMap<>();
            String query = response.getLocation().getQuery();
            Arrays.stream(query.split("&")).map(s -> s.split("=")).forEach(s -> queryMap.put(s[0], s[1]));
            sendAuthorizationRequest(
                smcbHandle,
                queryMap,
                smcbAuthCert,
                clientAttest,
                signatureType,
                authAction
            );
        }
    }

    // A_24944-01 - Anfrage des "AUTHORIZATION_CODE" für ein "ID_TOKEN"
    private void sendAuthorizationRequest(
        String smcbHandle,
        Map<String, String> queryMap,
        X509Certificate smcbAuthCert,
        String clientAttest,
        String signatureType,
        AuthAction authAction
    ) {
        String codeChallenge = queryMap.get("code_challenge");
        AuthorizationRequest authorizationRequest = AuthorizationRequest.builder()
            .link(discoveryDocumentResponse.getAuthorizationEndpoint())
            .clientId(queryMap.get("client_id"))
            .codeChallenge(codeChallenge)
            .codeChallengeMethod(CodeChallengeMethod.valueOf(queryMap.get("code_challenge_method")))
            .redirectUri(queryMap.get("redirect_uri"))
            .state(queryMap.get("state"))
            .scopes(Set.of(queryMap.get("scope").replace("+", " ")))
            .nonce(queryMap.get("nonce"))
            .build();

        authenticatorClient.doAuthorizationRequest(
            authorizationRequest,
            UnaryOperator.identity(),
            (HttpResponse<AuthenticationChallenge> authenticationChallenge) -> {
                authAction.execute(
                    authenticationChallenge.getBody(),
                    smcbAuthCert,
                    codeChallenge,
                    smcbHandle,
                    clientAttest,
                    signatureType
                );
            }
        );
    }
}