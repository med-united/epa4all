package de.servicehealth.epa4all.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.AuthenticationRequest;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.token.IdpJwe;
import de.gematik.idp.token.JsonWebToken;
import de.servicehealth.epa4all.idp.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.serviceport.IServicePortAggregator;

import java.security.cert.X509Certificate;
import java.util.function.UnaryOperator;

import static de.servicehealth.epa4all.idp.utils.IdpUtils.signServerChallengeAndEncrypt;

public abstract class AbstractAuthAction implements AuthAction {

    protected IServicePortAggregator servicePorts;
    protected AuthenticatorClient authenticatorClient;
    protected AuthorizationSmcBApi authorizationService;
    protected DiscoveryDocumentResponse discoveryDocumentResponse;

    public AbstractAuthAction(
        IServicePortAggregator servicePorts,
        AuthenticatorClient authenticatorClient,
        AuthorizationSmcBApi authorizationService,
        DiscoveryDocumentResponse discoveryDocumentResponse
    ) {
        this.servicePorts = servicePorts;
        this.authenticatorClient = authenticatorClient;
        this.authorizationService = authorizationService;
        this.discoveryDocumentResponse = discoveryDocumentResponse;
    }

    // A_20662 - Annahme des "user_consent" und des "CHALLENGE_TOKEN"
    protected AuthenticationResponse processAuthenticationChallenge(
        String smcbHandle,
        AuthenticationChallenge challengeBody,
        X509Certificate smcbAuthCert,
        String signatureType
    ) {
        JsonWebToken jsonWebToken = challengeBody.getChallenge();
        // A_20663-01 - Prüfung der Signatur des CHALLENGE_TOKEN
        // TODO:
        // jsonWebToken.verify(discoveryDocumentResponse.getIdpSig().getPublicKey());

        // A_20665-01 - Signatur der Challenge des IdP-Dienstes
        String signedChallenge = signServerChallengeAndEncrypt(
            servicePorts,
            discoveryDocumentResponse,
            smcbHandle,
            challengeBody.getChallenge().getRawString(),
            smcbAuthCert,
            signatureType,
            true
        );

        IdpJwe idpJwe = new IdpJwe(signedChallenge);
        AuthenticationRequest authenticationRequest = AuthenticationRequest.builder()
            .authenticationEndpointUrl(discoveryDocumentResponse.getAuthorizationEndpoint())
            .signedChallenge(idpJwe)
            .build();

        return authenticatorClient.performAuthentication(
            authenticationRequest, UnaryOperator.identity(), o -> {
            }
        );
    }
}
