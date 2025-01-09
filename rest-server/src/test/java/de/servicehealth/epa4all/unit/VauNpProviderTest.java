package de.servicehealth.epa4all.unit;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.EpaMultiService;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.vau.VauConfig;
import de.servicehealth.vau.VauFacade;
import io.smallrye.context.SmallRyeManagedExecutor;
import io.smallrye.context.SmallRyeThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.servicehealth.epa4all.server.idp.vaunp.VauNpFile.VAU_NP_FILE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        new File(TEST_FOLDER, VAU_NP_FILE_NAME).delete();
    }

    @Test
    public void vauNpProviderCreatesFileWithValueOnStart() throws Exception {
        String konnektorBaseUrl = "http://192.168.178.42";
        String epaBackend = "epa-as-1.dev.epa4all.de:443";
        String smcbHandle = "SMC-B-123";

        String vauNp = "MTJfMjAyNA";

        IKonnektorClient konnektorClient = mock(IKonnektorClient.class);
        when(konnektorClient.getSmcbHandle(any())).thenReturn(smcbHandle);

        EpaMultiService epaMultiService = mock(EpaMultiService.class);

        ConcurrentHashMap<String, EpaAPI> map = new ConcurrentHashMap<>();
        EpaAPI epaAPI = mock(EpaAPI.class);
        when(epaAPI.getAuthorizationSmcBApi()).thenReturn(mock(AuthorizationSmcBApi.class));
        when(epaAPI.getBackend()).thenReturn(epaBackend);
        when(epaAPI.getVauFacade()).thenReturn(mock(VauFacade.class));

        map.put(epaBackend, epaAPI);
        when(epaMultiService.getEpaBackendMap()).thenReturn(map);

        IdpClient idpClient = mock(IdpClient.class);
        when(idpClient.getVauNpSync(any(), any(), eq(smcbHandle), eq(epaBackend))).thenReturn(vauNp);

        EpaCallGuard epaCallGuard = new EpaCallGuard(new VauConfig());

        VauNpProvider vauNpProvider = new VauNpProvider(idpClient, epaCallGuard, epaMultiService, konnektorClient, null);
        vauNpProvider.setScheduledThreadPool(
            new SmallRyeManagedExecutor(
                3, 3, SmallRyeThreadContext.builder().build(), SmallRyeManagedExecutor.newThreadPoolExecutor(3, 3), "point"
            )
        );
        vauNpProvider.setKonnektorsConfigs(Map.of("8588_192.168.178.42", new KonnektorConfig()));
        vauNpProvider.setConfigFolder(TEST_FOLDER.getAbsolutePath());
        vauNpProvider.onStart();

        assertEquals(vauNp, vauNpProvider.getVauNp(smcbHandle, konnektorBaseUrl, epaBackend));
    }
}
