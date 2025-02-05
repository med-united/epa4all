package de.servicehealth.epa4all.unit;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.EpaConfig;
import de.service.health.api.epa4all.EpaMultiService;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.IdpConfig;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.model.GetNonce200Response;
import de.servicehealth.model.SendAuthCodeSC200Response;
import de.servicehealth.model.SendAuthCodeSCtype;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import io.smallrye.context.SmallRyeManagedExecutor;
import io.smallrye.context.SmallRyeThreadContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static de.health.service.cetp.konnektorconfig.FSConfigService.CONFIG_DELIMETER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VauNpProviderTest {

    public static final File TEST_FOLDER = new File("test-data");
    
    @BeforeEach
    public void beforeEach() {
        TEST_FOLDER.mkdir();
    }

    @AfterEach
    public void afterEach() {
        //new File(TEST_FOLDER, VAU_NP_FILE_NAME).delete();
    }

    @Test
    public void vauNpProviderCreatesFileWithValueOnStart() throws Exception {
        String konnektorHost = "192.168.178.42";
        String workplaceId = "1786_A1";
        String epaBackend = "epa-as-1.dev.epa4all.de:443";
        String smcbHandle = "SMC-B-123";

        String nonce = "2742738dhefuy3fg38f7";
        String vauNp = "MTJfMjAyNA";

        IKonnektorClient konnektorClient = mock(IKonnektorClient.class);
        when(konnektorClient.getSmcbHandle(any())).thenReturn(smcbHandle);

        EpaMultiService epaMultiService = mock(EpaMultiService.class);

        ConcurrentHashMap<String, EpaAPI> map = new ConcurrentHashMap<>();
        EpaAPI epaAPI = mock(EpaAPI.class);
        AuthorizationSmcBApi smcBApi = mock(AuthorizationSmcBApi.class);

        GetNonce200Response getNonce200Response = mock(GetNonce200Response.class);
        when(getNonce200Response.getNonce()).thenReturn(nonce);

        Response response = mock(Response.class);
        when(response.getLocation()).thenReturn(URI.create("https://uri.com?www=333"));

        SendAuthCodeSC200Response authCodeSC200Response = mock(SendAuthCodeSC200Response.class);
        when(authCodeSC200Response.getVauNp()).thenReturn(vauNp);
        when(smcBApi.sendAuthCodeSC(any(), any(), any(), any())).thenReturn(authCodeSC200Response);
        when(smcBApi.getNonce(any(), any(), any())).thenReturn(getNonce200Response);
        when(smcBApi.sendAuthorizationRequestSCWithResponse(any(), any(), any())).thenReturn(response);
        when(epaAPI.getAuthorizationSmcBApi()).thenReturn(smcBApi);
        when(epaAPI.getBackend()).thenReturn(epaBackend);
        when(epaAPI.getVauFacade()).thenReturn(mock(VauFacade.class));

        map.put(epaBackend, epaAPI);
        when(epaMultiService.getEpaBackendMap()).thenReturn(map);

        EpaConfig epaConfig = mock(EpaConfig.class);
        when(epaConfig.getEpaBackends()).thenReturn(Set.of(epaBackend));
        when(epaMultiService.getEpaConfig()).thenReturn(epaConfig);

        IdpConfig idpConfig = mock(IdpConfig.class);
        IdpClient idpClient = mock(IdpClient.class);

        SendAuthCodeSCtype authCodeSC = mock(SendAuthCodeSCtype.class);
        when(idpClient.getAuthCodeSync(any(), any(), any(), eq(smcbHandle))).thenReturn(authCodeSC);

        EpaCallGuard epaCallGuard = new EpaCallGuard(new VauConfig());

        VauNpProvider vauNpProvider = new VauNpProvider(idpConfig, idpClient, epaCallGuard, epaMultiService, konnektorClient, null);
        vauNpProvider.setScheduledThreadPool(
            new SmallRyeManagedExecutor(
                3, 3, SmallRyeThreadContext.builder().build(), SmallRyeManagedExecutor.newThreadPoolExecutor(3, 3), "point"
            )
        );
        vauNpProvider.setKonnektorsConfigs(Map.of(String.join(CONFIG_DELIMETER, "192.168.178.42", "1786_A1"), new KonnektorConfig()));
        vauNpProvider.setConfigFolder(TEST_FOLDER.getAbsolutePath());
        vauNpProvider.onStart(null);

        Optional<String> vauNpOpt = vauNpProvider.getVauNp(smcbHandle, konnektorHost, workplaceId, epaBackend);
        assertTrue(vauNpOpt.isPresent());
        assertEquals(vauNp, vauNpOpt.get());
    }
}
