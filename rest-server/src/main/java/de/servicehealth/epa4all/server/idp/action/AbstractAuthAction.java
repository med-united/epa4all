package de.servicehealth.epa4all.server.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.AuthenticationRequest;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.token.IdpJwe;
import de.gematik.idp.token.JsonWebToken;
import de.health.service.cetp.CertificateInfo;
import de.servicehealth.epa4all.server.idp.func.IdpFunc;
import org.jose4j.jwt.JwtClaims;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.function.UnaryOperator;

import static de.gematik.idp.field.ClaimName.NESTED_JWT;
import static de.servicehealth.epa4all.server.idp.utils.IdpUtils.getSignedJwt;

public abstract class AbstractAuthAction implements AuthAction {

    protected AuthenticatorClient authenticatorClient;
    protected DiscoveryDocumentResponse discoveryDocumentResponse;
    protected IdpFunc idpFunc;

    public AbstractAuthAction(
        IdpFunc idpFunc,
        AuthenticatorClient authenticatorClient,
        DiscoveryDocumentResponse discoveryDocumentResponse
    ) {
        this.idpFunc = idpFunc;
        this.authenticatorClient = authenticatorClient;
        this.discoveryDocumentResponse = discoveryDocumentResponse;
    }

    // A_20662 - Annahme des "user_consent" und des "CHALLENGE_TOKEN"
    protected AuthenticationResponse processAuthenticationChallenge(
        String smcbHandle,
        CertificateInfo certificateInfo,
        AuthenticationChallenge authChallenge
    ) throws NoSuchAlgorithmException, IOException {
        // A_20663-01 - Prüfung der Signatur des CHALLENGE_TOKEN
        // TODO:
        // JsonWebToken jsonWebToken = authChallenge.getChallenge();
        // jsonWebToken.verify(discoveryDocumentResponse.getIdpSig().getPublicKey());

        // A_20665-01 - Signatur der Challenge des IdP-Dienstes
        JwtClaims claims = new JwtClaims();
        claims.setClaim(NESTED_JWT.getJoseName(), authChallenge.getChallenge().getRawString());
        String signedJwt = getSignedJwt(certificateInfo, claims.toJson(), smcbHandle, false, idpFunc);

        // A_20667-01 - Response auf die Challenge des Authorization-Endpunktes
        JsonWebToken jsonWebToken = new JsonWebToken(signedJwt);
        IdpJwe encryptAsNjwt = jsonWebToken.encryptAsNjwt(discoveryDocumentResponse.getIdpEnc());
        String signedChallenge = encryptAsNjwt.getRawString();
        IdpJwe idpJwe = new IdpJwe(signedChallenge);

        AuthenticationRequest authenticationRequest = AuthenticationRequest.builder()
            .authenticationEndpointUrl(discoveryDocumentResponse.getAuthorizationEndpoint())
            .signedChallenge(idpJwe)
            .build();

        return authenticatorClient.performAuthentication(authenticationRequest, UnaryOperator.identity(), o -> {
        });
    }
}
