package de.servicehealth.epa4all;

import de.servicehealth.epa4all.config.EpaConfig;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.idp.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.vau.VauClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class EpaProvider {

    // TODO All other APIs will be provided by rest-server dedicated provider

    @Inject
    EpaConfig epaConfig;

    @Inject
    VauClient vauClient;

    @Produces
    AuthorizationSmcBApi getAuthorizationApi() throws Exception {
        String serviceUrl = epaConfig.getAuthorizationServiceUrl();
        return epaConfig.isProxy()
            ? ClientFactory.createProxyClient(vauClient, AuthorizationSmcBApi.class,serviceUrl)
            : ClientFactory.createPlainClient(AuthorizationSmcBApi.class,serviceUrl);
    }
}
