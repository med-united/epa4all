package de.servicehealth.epa4all.server.idp;

import de.gematik.idp.client.AuthenticatorClient;
import de.servicehealth.epa4all.server.cetp.KonnektorClient;
import de.servicehealth.epa4all.server.idp.authorization.TSSClient;
import de.servicehealth.epa4all.server.serviceport.MultiKonnektorService;
import de.servicehealth.startup.StartupEventListener;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;

@TSSClient
@ApplicationScoped
public class TSSIdpClient extends IdpClient implements StartupEventListener {

    private static final String DISCOVERY_DOC_FILE_NAME_TSS = "tss-discovery-doc";

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public TSSIdpClient(
        TssIdpConfig tssIdpConfig,
        ManagedExecutor managedExecutor,
        KonnektorClient konnektorClient,
        @TSSClient
        AuthenticatorClient tssAuthenticatorClient,
        MultiKonnektorService multiKonnektorService
    ) {
        super(
            new IdpConfig(
                tssIdpConfig.getClientId(),
                tssIdpConfig.getServiceUrl(),
                tssIdpConfig.getAuthRequestUrl(),
                tssIdpConfig.getAuthRequestRedirectUrl(),
                tssIdpConfig.isHcvEnabled()
            ),
            managedExecutor,
            konnektorClient,
            tssAuthenticatorClient,
            multiKonnektorService
        );
    }

    @Override
    protected String getFileName() {
        return DISCOVERY_DOC_FILE_NAME_TSS;
    }
}
