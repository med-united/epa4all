package de.servicehealth.epa4all.server.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.health.service.cetp.CertificateInfo;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public interface AuthAction {

    void execute(
        String epaNonce,
        String smcbHandle,
        CertificateInfo certificateInfo,
        AuthenticationChallenge authChallenge
    ) throws NoSuchAlgorithmException, IOException;
}