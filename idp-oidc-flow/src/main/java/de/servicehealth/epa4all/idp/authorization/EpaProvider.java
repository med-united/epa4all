package de.servicehealth.epa4all.idp.authorization;

import de.servicehealth.epa4all.config.EpaAuthConfig;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.vau.VauClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

// TODO - provide all the APIs with the dedicated provider in rest-server

@ApplicationScoped
public class EpaProvider {

    @Inject
    EpaAuthConfig epaAuthConfig;

    @Inject
    VauClient vauClient;

    @Produces
    AuthorizationSmcBApi getAuthorizationApi() throws Exception {
        String serviceUrl = epaAuthConfig.getAuthorizationServiceUrl();
        return epaAuthConfig.isProxy()
            ? ClientFactory.createProxyClient(vauClient, AuthorizationSmcBApi.class,serviceUrl)
            : ClientFactory.createPlainClient(AuthorizationSmcBApi.class,serviceUrl);
    }
}
