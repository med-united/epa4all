package de.servicehealth.epa4all.server.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.AuthenticationRequest;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.field.ClaimName;
import de.gematik.idp.token.IdpJwe;
import de.gematik.idp.token.JsonWebToken;
import de.servicehealth.epa4all.server.idp.func.IdpFunc;
import org.jose4j.jwt.JwtClaims;

import java.security.cert.X509Certificate;
import java.util.function.UnaryOperator;

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

    private String signServerChallengeAndEncrypt(
        DiscoveryDocumentResponse discoveryDocumentResponse,
        String smcbHandle,
        String challengeToSign,
        X509Certificate certificate,
        String signatureType,
        boolean encrypt
    ) {
        final JwtClaims claims = new JwtClaims();
        claims.setClaim(ClaimName.NESTED_JWT.getJoseName(), challengeToSign);
        JsonWebToken jsonWebToken = signClaimsAndReturnJWT(certificate, claims, signatureType, smcbHandle);
        if (encrypt) {
            IdpJwe encryptAsNjwt = jsonWebToken
                // A_20667-01 - Response auf die Challenge des Authorization-Endpunktes
                .encryptAsNjwt(discoveryDocumentResponse.getIdpEnc());
            return encryptAsNjwt.getRawString();
        } else {
            return jsonWebToken.getRawString();
        }
    }

    private JsonWebToken signClaimsAndReturnJWT(
        X509Certificate certificate,
        final JwtClaims claims,
        String signatureType,
        String smcbHandle
    ) {
        final String signedJwt = getSignedJwt(certificate, claims, signatureType, smcbHandle, false, idpFunc);
        return new JsonWebToken(signedJwt);
    }
}
