package de.servicehealth.epa4all.server.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;

import java.security.cert.X509Certificate;

public interface AuthAction {

    void execute(
        AuthenticationChallenge authChallenge,
        X509Certificate smcbAuthCert,
        String codeChallenge,
        String smcbHandle,
        String clientAttest,
        String signatureType
    );
}