package de.servicehealth.epa4all.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;

import java.security.cert.X509Certificate;

public interface AuthAction {

    String URN_BSI_TR_03111_ECDSA = "urn:bsi:tr:03111:ecdsa";
    String USER_AGENT = "ServiceHealth/1.0";

    void execute(
        AuthenticationChallenge challengeBody,
        ContextType contextType,
        String smcbHandle,
        X509Certificate smcbAuthCert,
        String clientAttest,
        String signatureType
    );
}
