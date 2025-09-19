package de.servicehealth.epa4all.integration.bc.epa;

import de.servicehealth.api.epa4all.EpaAPI;
import de.servicehealth.api.epa4all.EpaMultiService;
import de.servicehealth.api.epa4all.authorization.AuthorizationSmcbAPI;
import de.servicehealth.epa4all.common.profile.IdpEpaProfile;
import de.servicehealth.epa4all.cxf.client.ClientFactory;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.serviceport.ServicePortProvider;
import de.servicehealth.vau.VauClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(IdpEpaProfile.class)
public class IdpClientEpaIT {

    private static final Logger log = LoggerFactory.getLogger(IdpClientEpaIT.class.getName());

    @Inject
    IdpClient idpClient;

    @Inject
    ClientFactory clientFactory;

    @Inject
    VauNpProvider vauNpProvider;

    @Inject
    EpaMultiService epaMultiService;

    @Inject
    DefaultUserConfig defaultUserConfig;

    @Inject
    ServicePortProvider servicePortProvider;

    @BeforeEach
    public void before() throws Exception {
        new File("config/konnektoren/discovery-doc").delete();
        idpClient.doStart();
        clientFactory.doStart();
        epaMultiService.doStart();
        servicePortProvider.doStart();
    }

    @Test
    public void vauNpObtained() throws Exception {
        EpaAPI epaAPI = epaMultiService.findEpaAPI("X110624006");
        String backend = epaAPI.getBackend();

        // A_24881 - Nonce anfordern für Erstellung "Attestation der Umgebung"
        AuthorizationSmcbAPI authorizationSmcbAPI = epaAPI.getAuthorizationSmcbAPI();
        VauClient vauClient = epaAPI.getVauFacade().getEmptyClients().getFirst();

        vauNpProvider.reloadVauClient(
            authorizationSmcbAPI,
            defaultUserConfig,
            vauClient.getKonnektorWorkplace(),
            vauClient,
            backend
        );
        assertNotNull(vauClient.getVauNp());
    }
}