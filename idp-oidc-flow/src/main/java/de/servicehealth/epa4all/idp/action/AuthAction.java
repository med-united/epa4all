package de.servicehealth.epa4all.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;

import java.security.cert.X509Certificate;

public interface AuthAction {

    String URN_BSI_TR_03111_ECDSA = "urn:bsi:tr:03111:ecdsa";
    String USER_AGENT = "ServiceHealth/1.0";

    void execute(
        AuthenticationChallenge authChallenge,
        X509Certificate smcbAuthCert,
        String smcbHandle,
        String clientAttest,
        String signatureType
    );
}
