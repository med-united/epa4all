package de.servicehealth.epa4all.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.AuthenticationRequest;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.field.ClaimName;
import de.gematik.idp.token.IdpJwe;
import de.gematik.idp.token.JsonWebToken;
import de.service.health.api.epa4all.MultiEpaService;
import de.service.health.api.serviceport.IKonnektorServicePortsAPI;
import org.jose4j.jwt.JwtClaims;

import java.security.cert.X509Certificate;
import java.util.function.UnaryOperator;

import static de.servicehealth.epa4all.idp.utils.IdpUtils.getSignedJwt;

public abstract class AbstractAuthAction implements AuthAction {

    protected MultiEpaService multiEpaService;
    protected IKonnektorServicePortsAPI servicePorts;
    protected AuthenticatorClient authenticatorClient;
    protected DiscoveryDocumentResponse discoveryDocumentResponse;

    public AbstractAuthAction(
        MultiEpaService multiEpaService,
        IKonnektorServicePortsAPI servicePorts,
        AuthenticatorClient authenticatorClient,
        DiscoveryDocumentResponse discoveryDocumentResponse
    ) {
        this.servicePorts = servicePorts;
        this.multiEpaService = multiEpaService;
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

    private String signServerChallengeAndEncrypt(
        IKonnektorServicePortsAPI servicePorts,
        DiscoveryDocumentResponse discoveryDocumentResponse,
        String smcbHandle,
        String challengeToSign,
        X509Certificate certificate,
        String signatureType,
        boolean encrypt
    ) {
        final JwtClaims claims = new JwtClaims();
        claims.setClaim(ClaimName.NESTED_JWT.getJoseName(), challengeToSign);
        JsonWebToken jsonWebToken = signClaimsAndReturnJWT(servicePorts, certificate, claims, signatureType, smcbHandle);
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
        IKonnektorServicePortsAPI servicePorts,
        X509Certificate certificate,
        final JwtClaims claims,
        String signatureType,
        String smcbHandle
    ) {
        final String signedJwt = getSignedJwt(servicePorts, certificate, claims, signatureType, smcbHandle, false);
        return new JsonWebToken(signedJwt);
    }
}
