package de.servicehealth.epa4all.server.rest.idp;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.cdi.FromHttpPath;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.authorization.TSSClient;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@RequestScoped
@Path("/tss")
public class TSS {

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
        return idpClient.getAccessToken(smcbHandle, userRuntimeConfig);
    }
}