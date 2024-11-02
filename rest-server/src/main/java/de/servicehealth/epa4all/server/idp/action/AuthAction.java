package de.servicehealth.epa4all.server.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;

import java.security.cert.X509Certificate;

public interface AuthAction {

    String URN_BSI_TR_03111_ECDSA = "urn:bsi:tr:03111:ecdsa";

    void execute(
        AuthenticationChallenge authChallenge,
        X509Certificate smcbAuthCert,
        String codeChallenge,
        String smcbHandle,
        String clientAttest,
        String signatureType
    );
}
