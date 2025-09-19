package de.servicehealth.epa4all.server.idp.action;

import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.servicehealth.epa4all.server.idp.func.IdpFunc;

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
}
