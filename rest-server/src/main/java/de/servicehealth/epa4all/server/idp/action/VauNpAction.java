package de.servicehealth.epa4all.server.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.AuthenticationRequest;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.field.ClaimName;
import de.gematik.idp.token.IdpJwe;
import de.gematik.idp.token.JsonWebToken;
import de.health.service.cetp.CertificateInfo;
import de.servicehealth.epa4all.server.idp.func.IdpFunc;
import de.servicehealth.model.SendAuthCodeSCtype;
import org.jose4j.jwt.JwtClaims;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static de.gematik.idp.field.ClaimName.NESTED_JWT;
import static de.servicehealth.epa4all.server.idp.utils.IdpUtils.getSignedJwt;

public class VauNpAction extends AbstractAuthAction {

    private final Consumer<SendAuthCodeSCtype> authConsumer;

    public VauNpAction(
        AuthenticatorClient authenticatorClient,
        DiscoveryDocumentResponse discoveryDocumentResponse,
        Consumer<SendAuthCodeSCtype> authConsumer,
        IdpFunc idpFunc
    ) {
        super(idpFunc, authenticatorClient, discoveryDocumentResponse);
        this.authConsumer = authConsumer;
    }

    @Override
    public void execute(
        String epaNonce,
        String smcbHandle,
        String codeChallenge,
        CertificateInfo certificateInfo,
        AuthenticationChallenge authChallenge
    ) throws NoSuchAlgorithmException, IOException {
        JwtClaims claims = new JwtClaims();
        claims.setClaim(ClaimName.NONCE.getJoseName(), epaNonce);
        claims.setClaim(ClaimName.ISSUED_AT.getJoseName(), System.currentTimeMillis() / 1000);
        claims.setClaim(ClaimName.EXPIRES_AT.getJoseName(), (System.currentTimeMillis() / 1000) + 1200);

        // A_24882-01 - Signatur clientAttest
        X509Certificate smcbAuthCert = certificateInfo.getCertificate();
        String clientAttest = getSignedJwt(
            smcbAuthCert,
            claims,
            certificateInfo.getSignatureType(),
            smcbHandle,
            true,
            idpFunc
        );
        AuthenticationResponse authenticationResponse = processAuthenticationChallenge(
            smcbHandle, certificateInfo.getSignatureType(), authChallenge, smcbAuthCert
        );

        SendAuthCodeSCtype sendAuthCodeSC = new SendAuthCodeSCtype();
        sendAuthCodeSC.setAuthorizationCode(authenticationResponse.getCode());
        sendAuthCodeSC.setClientAttest(clientAttest);
        authConsumer.accept(sendAuthCodeSC);
    }

    // A_20662 - Annahme des "user_consent" und des "CHALLENGE_TOKEN"
    private AuthenticationResponse processAuthenticationChallenge(
        String smcbHandle,
        String signatureType,
        AuthenticationChallenge authChallenge,
        X509Certificate smcbAuthCert
    ) throws NoSuchAlgorithmException, IOException {
        JsonWebToken jsonWebToken = authChallenge.getChallenge();
        // A_20663-01 - Prüfung der Signatur des CHALLENGE_TOKEN
        // TODO:
        // jsonWebToken.verify(discoveryDocumentResponse.getIdpSig().getPublicKey());

        // A_20665-01 - Signatur der Challenge des IdP-Dienstes
        String signedChallenge = signServerChallengeAndEncrypt(
            discoveryDocumentResponse,
            smcbHandle,
            authChallenge.getChallenge().getRawString(),
            smcbAuthCert,
            signatureType,
            true
        );

        IdpJwe idpJwe = new IdpJwe(signedChallenge);
        AuthenticationRequest authenticationRequest = AuthenticationRequest.builder()
            .authenticationEndpointUrl(discoveryDocumentResponse.getAuthorizationEndpoint())
            .signedChallenge(idpJwe)
            .build();

        return authenticatorClient.performAuthentication(authenticationRequest, UnaryOperator.identity(), o -> {
        });
    }

    private String signServerChallengeAndEncrypt(
        DiscoveryDocumentResponse discoveryDocumentResponse,
        String smcbHandle,
        String authChallenge,
        X509Certificate certificate,
        String signatureType,
        boolean encrypt
    ) throws NoSuchAlgorithmException, IOException {
        final JwtClaims claims = new JwtClaims();
        claims.setClaim(NESTED_JWT.getJoseName(), authChallenge);

        final String signedJwt = getSignedJwt(certificate, claims, signatureType, smcbHandle, false, idpFunc);
        JsonWebToken jsonWebToken = new JsonWebToken(signedJwt);
        if (encrypt) {
            // A_20667-01 - Response auf die Challenge des Authorization-Endpunktes
            IdpJwe encryptAsNjwt = jsonWebToken.encryptAsNjwt(discoveryDocumentResponse.getIdpEnc());
            return encryptAsNjwt.getRawString();
        } else {
            return jsonWebToken.getRawString();
        }
    }
}