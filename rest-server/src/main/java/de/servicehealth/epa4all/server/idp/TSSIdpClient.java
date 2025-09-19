package de.servicehealth.epa4all.server.idp;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.servicehealth.epa4all.server.idp.authorization.TSSClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import de.gematik.idp.client.AuthenticatorClient;

import de.servicehealth.epa4all.server.cetp.KonnektorClient;
import de.servicehealth.epa4all.server.serviceport.MultiKonnektorService;

@TSSClient
@ApplicationScoped
public class TSSIdpClient extends IdpClient {

    private final static Logger log = LoggerFactory.getLogger(IdpClient.class.getName());

    public TSSIdpClient() {
        // Default constructor for CDI
    }

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public TSSIdpClient(
        ManagedExecutor managedExecutor,
        KonnektorClient konnektorClient,
        AuthenticatorClient authenticatorClient,
        MultiKonnektorService multiKonnektorService
    ) {
        super(
            new IdpConfig("116117TerminserviceApp", 
                                            "https://ref1-ets-ti-auth-server.kv-telematik.de", 
                                            "https://ref1-ets-ti-auth-server.kv-telematik.de/auth/authorize", 
                                            "https://localhost/", false),
            managedExecutor,
            konnektorClient,
            authenticatorClient,
            multiKonnektorService
        );
    }

    @Override
    public void doStart() throws Exception {
        log.info("TSSIdpClient onStart");
        super.doStart();
    }
    
}
