package de.servicehealth.epa4all.server.rest.idp;


import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.inject.Inject;
import de.health.service.cetp.IKonnektorClient;
import de.servicehealth.epa4all.server.cdi.FromHttpPath;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.TSSIdpClient;
import de.servicehealth.epa4all.server.idp.authorization.TSSClient;
import de.gematik.idp.client.AuthenticatorClient;
import de.health.service.config.api.UserRuntimeConfig;

@Path("/tss")
public class TSS {

    @Inject
    @TSSClient
    AuthenticatorClient tssAuthenticatorClient;

    @Inject
    IKonnektorClient konnektorClient;

    @Inject
    @FromHttpPath
    UserRuntimeConfig userRuntimeConfig;

    @Inject
    @TSSClient
    IdpClient idpClient;


    @GET
    @Path("/token")
    public String getToken() throws Exception {
        String smcbHandle = konnektorClient.getSmcbHandle(userRuntimeConfig);
        idpClient.setAuthenticatorClient(tssAuthenticatorClient);
        idpClient.doStart();
        // Assuming the IdpClient has a method to get a token, which is not shown
        // in the provided code snippets. This is a placeholder.
        return idpClient.getAccessToken(smcbHandle, userRuntimeConfig); // Replace with actual method to get token
    }

}
