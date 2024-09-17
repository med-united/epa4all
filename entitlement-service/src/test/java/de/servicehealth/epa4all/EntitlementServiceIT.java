package de.servicehealth.epa4all;

import de.servicehealth.api.EntitlementsApi;
import de.servicehealth.api.EntitlementsEPaFdVApi;
import de.servicehealth.epa4all.common.DevTestProfile;
import de.servicehealth.epa4all.cxf.interceptor.VauInterceptor;
import de.servicehealth.model.ActorIdType;
import de.servicehealth.model.EntitlementClaimsResponseType;
import de.servicehealth.model.EntitlementRequestType;
import de.servicehealth.model.GetEntitlements200Response;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.client.Client;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import de.servicehealth.epa4all.cxf.jsonb.JsonbProvider;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static de.servicehealth.epa4all.common.Utils.createFakeSSLContext;
import static de.servicehealth.epa4all.common.Utils.isDockerServiceRunning;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestProfile(DevTestProfile.class)
public class EntitlementServiceIT {

    private final static Logger log = LoggerFactory.getLogger(EntitlementServiceIT.class);

    @Inject
    @ConfigProperty(name = "entitlement-service.url")
    String entitlementServiceUrl;

    @Test
    public void getEntitlementServiceWorks() throws Exception {
        if (isDockerServiceRunning("entitlement-service")) {
            EntitlementsEPaFdVApi api = JAXRSClientFactory.create(
                entitlementServiceUrl, EntitlementsEPaFdVApi.class, getProviders()
            );

            initApi(api);

            assertThrows(NotFoundException.class, () -> {
                GetEntitlements200Response response = api.getEntitlements("Z123456789", "CLIENTID1234567890AB/2.1.12-45");
                assertNotNull(response);
            });
        }
    }

    @Test
    public void postEntitlementServiceWorks() throws Exception {
        if (isDockerServiceRunning("entitlement-service")) {

            String jwt = "eyJlbmMiOiJBMjU2R0NNIiwiY3R5IjoiTkpXVCIsImV4cCI6MTcyNjU3MDE3MiwiYWxnIjoiZGlyIiwia2lkIjoiMDAwMSJ9.5ZMyygoicdXW77Fr.90Q1b4sdSKu8DpqGqQthUFhpKKASBuzmxiB5FGepazcMZvH95FtpYwLWrsxeEIPCLRworsO0r-OHMSd1lGIw5b8Fa7doXsh4zfrkCeXHxBX0qIzXtC6m84Dp1SoRgn9Q8ZkNKsXwDMKwJKV_v2TxohORYrc1NJSXeQm4S8jBzG0PQ4pGIjL7wu5uauWyjDoQnY_LB8T33Lr5sb6269HJCV0ThkalaYdZ7dh8FnYJWywUAn4Pdq7589W0gIz-t1jAc-rF_JXtRv5Ya6XIvutEeePhdfgPvslR5BYZyQ6thWYeC7pvhbHfjhhYYXT3E57ylolYP9tP6HU2ZEWoOtndcVj0yVQF86Qve6tmAenwNfiu4TBu1nUIlikTFF6Z0cBqiS45ETUUtn5pRGiSRQrPXN2tSzXSqlRiVjZHysNxJ9pbxJwcRe4LNpz2KgEI0GaO9Yt5_dYmoVgBiR4ulJ1ztxNP7rBlDr8w14YCVR8ZZefTUdC66xOTDVqbM8QMXAm8x-ZSYfxHRSdmCo4QqyV6UyHJbhQJfHKZwdGslQ-TgMDyRV5mvOgVd9Tw6oTmW1bS1fLFjI2OqHXuGIWAXi0Ti56yz2a8i2RxpiS3sEp7w6NHSRHLeLYMeQuhs1Ftcqdynefor4Zy3L4QvFroOiRgzVLiy7HPCjqpi4tf1FJXH0TjxgQ0OFBbo-jiA9h4EdyO9FvStCBzoiInIKCPi4Id_sWOhHtyvPkrPVQdBjxIoQktA6ILC1O-fqOnp9AzoTwvxAtBWSzLj6tSKy28ESciIQlpHDasu0XerGZH1Elfpx22PRi8julAvn9siLjvK1JTsQR4lSs6n3HgKUj1BEt9pT02FVVBBFaYzUNUH4qCzqTgpIlPdJGSuCkZq-gChrebUzGDvwhYZHVEsCjF1UMvCollANrN5FGergjtwQVPr6fbgGhzk6s3VVgrMy26jrObTxX_2UdEBdOgD87ZiZoR_8RqfUgqGZmsKeJhEgsNfmvWf4f6ZhQv6p2VzDC9x1vP_T90B34t0xvnVkgJZHozjRAGsT5-zGk0C0IdSCRS8Dp2nfMVwaOCCrYjdotrt9E5VJhYPHlHYR3pVacDRaHlxbtTXf1Ys8NaodHkIWCloP1x8HpRvZOlc-4tZGxRMqNKZSBI188mEqzunwFyfn6-IlLWGuCX8tSfILcGYWcuPNq0UKpm3pCEvOtML1WUwgsLM09g09vgxtWHGcKif_gsvnJHYIEfNZLSVI97vtcBeWT2AqH-pYRNTYMOmRCaVt_ZUsJJjNzpWx2g7mbfxmL65C30QAXmRKmcR_VEGKE0sLr68_FbzqYMitPFsj5c1xbd--ck7_xkDSK8MC3dQvJh7LgPyFiDQRenDeOQoWEXh4__jQaIIEWTruITFIOQF4wOCf-UGRVNtWPKEQ1xI1a0XMBriV2oIEphAUbyZnIwkA.g4FNfSkXdp2st4lECdRokg";

            EntitlementsApi api = JAXRSClientFactory.create(
                entitlementServiceUrl, EntitlementsApi.class, getProviders()
            );

            initApi(api);

            EntitlementRequestType requestType = new EntitlementRequestType().jwt(jwt);
            assertDoesNotThrow(() -> api.setEntitlementPs("Z123456789", "CLIENTID1234567890AB/2.1.12-45", requestType));
        } else {
            log.warn("Docker container for entitlement-service is not running, skipping a test");
        }
    }

    private void initApi(Object api) throws Exception {
        Client client = WebClient.client(api);
        client.type("application/json").accept("application/json");

        ClientConfiguration config = WebClient.getConfig(client);
        config.getOutInterceptors().add(new VauInterceptor());

        HTTPConduit conduit = (HTTPConduit) config.getConduit();
        conduit.getClient().setVersion("1.1");

        TLSClientParameters tlsParams = conduit.getTlsClientParameters();
        if (tlsParams == null) {
            tlsParams = new TLSClientParameters();
            conduit.setTlsClientParameters(tlsParams);
        }

        tlsParams.setSslContext(createFakeSSLContext());
        tlsParams.setDisableCNCheck(true);
        tlsParams.setHostnameVerifier((hostname, session) -> true);

        config.getOutInterceptors().add(new LoggingOutInterceptor());
        config.getInInterceptors().add(new LoggingInInterceptor());
    }

    private static List<JsonbProvider> getProviders() {
        JsonbProvider provider = new JsonbProvider();
        List<JsonbProvider> providers = new ArrayList<>();
        providers.add(provider);
        return providers;
    }
}
