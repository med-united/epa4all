package de.servicehealth.epa4all.unit;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.cetp.konnektorconfig.KonnektorsConfigs;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.api.epa4all.EpaConfig;
import de.servicehealth.api.epa4all.EpaMultiService;
import de.servicehealth.api.epa4all.authorization.AuthorizationSmcbAPI;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.IdpConfig;
import de.servicehealth.epa4all.server.idp.vaunp.VauHandshake;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.vau.VauClient;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VauNpProviderTest {

    private static final String BACKEND = "epa-backend";
    private static final String SMCB_HANDLE = "SMC-B-1";

    private final IKonnektorClient konnektorClient = mock(IKonnektorClient.class);
    private final VauHandshake vauHandshake = mock(VauHandshake.class);
    private final AuthorizationSmcbAPI smcbAPI = mock(AuthorizationSmcbAPI.class);
    private final UserRuntimeConfig runtimeConfig = mock(UserRuntimeConfig.class);

    private VauNpProvider vauNpProvider;
    private VauClient vauClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        EpaConfig epaConfig = mock(EpaConfig.class);
        when(epaConfig.getEpaBackends()).thenReturn(List.of(BACKEND));
        when(epaConfig.getEpaUserAgent()).thenReturn("user-agent");
        EpaMultiService epaMultiService = mock(EpaMultiService.class);
        when(epaMultiService.getEpaConfig()).thenReturn(epaConfig);

        Instance<VauHandshake> vauHandshakeInstance = mock(Instance.class);
        when(vauHandshakeInstance.get()).thenReturn(vauHandshake);

        when(konnektorClient.getSmcbHandle(runtimeConfig)).thenReturn(SMCB_HANDLE);

        vauNpProvider = new VauNpProvider(
            mock(IdpConfig.class),
            mock(IdpClient.class),
            mock(EpaCallGuard.class),
            epaMultiService,
            mock(ManagedExecutor.class),
            konnektorClient,
            vauHandshakeInstance,
            mock(KonnektorsConfigs.class),
            mock(KonnektorDefaultConfig.class)
        );
        vauClient = new VauClient(false, true, 1000, "konnektor<sep>workplace", null, null);
    }

    @Test
    void unverifiedPinSkipsHandshakeAndEpaCalls() throws Exception {
        when(konnektorClient.getSmcbPinStatus(runtimeConfig, SMCB_HANDLE)).thenReturn("VERIFIABLE");

        vauNpProvider.reloadVauClient(smcbAPI, runtimeConfig, vauClient.getKonnektorWorkplace(), vauClient, BACKEND);

        verify(vauHandshake, never()).apply(anyString(), any());
        verifyNoInteractions(smcbAPI);
        assertNull(vauClient.getVauNp());
        assertNotNull(vauClient.getException());
        assertTrue(vauClient.getException().getMessage().contains("VERIFIABLE"));
    }

    @Test
    void verifiedPinProceedsToHandshake() throws Exception {
        when(konnektorClient.getSmcbPinStatus(runtimeConfig, SMCB_HANDLE)).thenReturn("VERIFIED");
        when(vauHandshake.apply(anyString(), any())).thenReturn(false);

        vauNpProvider.reloadVauClient(smcbAPI, runtimeConfig, vauClient.getKonnektorWorkplace(), vauClient, BACKEND);

        verify(vauHandshake).apply("https://" + BACKEND, vauClient);
    }
}
