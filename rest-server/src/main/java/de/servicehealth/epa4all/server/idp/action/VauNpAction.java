package de.servicehealth.epa4all.server.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.health.service.cetp.CertificateInfo;
import de.servicehealth.epa4all.server.idp.func.IdpFunc;
import de.servicehealth.model.SendAuthCodeSCtype;
import org.jose4j.jwt.JwtClaims;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;

import static de.gematik.idp.field.ClaimName.EXPIRES_AT;
import static de.gematik.idp.field.ClaimName.ISSUED_AT;
import static de.gematik.idp.field.ClaimName.NONCE;
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
        CertificateInfo certificateInfo,
        AuthenticationChallenge authChallenge
    ) throws NoSuchAlgorithmException, IOException {
        AuthenticationResponse authenticationResponse = processAuthenticationChallenge(
            smcbHandle, certificateInfo, authChallenge
        );

        SendAuthCodeSCtype sendAuthCodeSC = new SendAuthCodeSCtype();
        sendAuthCodeSC.setAuthorizationCode(authenticationResponse.getCode());
        sendAuthCodeSC.setClientAttest(prepareClientAttest(epaNonce, smcbHandle, certificateInfo));
        authConsumer.accept(sendAuthCodeSC);
    }

    private String prepareClientAttest(
        String epaNonce,
        String smcbHandle,
        CertificateInfo certificateInfo
    ) throws NoSuchAlgorithmException, IOException {
        JwtClaims claims = new JwtClaims();
        claims.setClaim(NONCE.getJoseName(), epaNonce);
        claims.setClaim(ISSUED_AT.getJoseName(), System.currentTimeMillis() / 1000);
        claims.setClaim(EXPIRES_AT.getJoseName(), (System.currentTimeMillis() / 1000) + 1200);

        // A_24882-01 - Signatur clientAttest
        return getSignedJwt(certificateInfo, claims.toJson(), smcbHandle, true, idpFunc);
    }
}