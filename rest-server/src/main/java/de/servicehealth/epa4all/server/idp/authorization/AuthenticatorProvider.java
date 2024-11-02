package de.servicehealth.epa4all.server.idp.authorization;

import de.gematik.idp.client.AuthenticatorClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class AuthenticatorProvider {

    @Produces
    @Singleton
    AuthenticatorClient getAuthenticatorClient() {
        return new AuthenticatorClient();
    }
}