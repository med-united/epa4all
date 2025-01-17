package de.servicehealth.epa4all.server.idp;

import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

@Getter
@Setter
@NoArgsConstructor
public class DiscoveryDocumentWrapper implements Serializable {

    @Serial
    private static final long serialVersionUID = 4597996348076108959L;

    private String authorizationEndpoint;
    private String ssoEndpoint;
    private String tokenEndpoint;
    private String pairingEndpoint;
    private String authPairEndpoint;
    private X509Certificate idpSig;
    private PublicKey idpEnc;
    private X509Certificate discSig;

    public DiscoveryDocumentWrapper(DiscoveryDocumentResponse documentResponse) {
        this.authorizationEndpoint = documentResponse.getAuthorizationEndpoint();
        this.ssoEndpoint = documentResponse.getSsoEndpoint();
        this.tokenEndpoint = documentResponse.getTokenEndpoint();
        this.pairingEndpoint = documentResponse.getPairingEndpoint();
        this.authPairEndpoint = documentResponse.getAuthPairEndpoint();
        this.idpSig = documentResponse.getIdpSig();
        this.idpEnc = documentResponse.getIdpEnc();
        this.discSig = documentResponse.getDiscSig();
    }

    public DiscoveryDocumentResponse toDiscoveryDocumentResponse() {
        return new DiscoveryDocumentResponse(
            authorizationEndpoint, ssoEndpoint, tokenEndpoint, pairingEndpoint, authPairEndpoint, idpSig, idpEnc, discSig
        );
    }
}
