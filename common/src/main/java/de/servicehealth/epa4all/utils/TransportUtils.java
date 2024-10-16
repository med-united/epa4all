package de.servicehealth.epa4all.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class TransportUtils {

    private static final Logger log = LoggerFactory.getLogger(TransportUtils.class);

    public static TrustManager[] getFakeTrustManagers() {
        return new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
    }

    public static SSLContext createFakeSSLContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        TrustManager[] trustManagers = getFakeTrustManagers();
        sslContext.init(null, trustManagers, new SecureRandom());
        return sslContext;
    }

    public static void printCborMessage(
        byte[] message,
        String vauCid,
        String vauDebugSC,
        String vauDebugCS,
        String contentLength
    ) throws IOException {
        final JsonNode message2Tree = new CBORMapper().readTree(message);

        if (vauCid != null) {
            log.info("\n\nVAU MESSAGE {}, length [{}]\nVAU-CID: {}\nVAU-DEBUG-S_K1_s2c: {}\nVAU-DEBUG-S_K1_c2s: {}\nKyber768_ct: {}\nAEAD_ct: {}\n\nECDH_ct: {}",
                message2Tree.get("MessageType").textValue(),
                contentLength,
                vauCid,
                vauDebugSC,
                vauDebugCS,
                Base64.getEncoder().encodeToString(message2Tree.get("Kyber768_ct").binaryValue()),
                Base64.getEncoder().encodeToString(message2Tree.get("AEAD_ct").binaryValue()),
                message2Tree.get("ECDH_ct").toString()
            );
        } else {
            log.info("\n\nVAU MESSAGE {}, length [{}]\nVAU-DEBUG-S_K2_s2c_INFO: {}\nVAU-DEBUG-S_K2_c2s_INFO: {}\nAEAD_ct_key_confirmation: {}",
                message2Tree.get("MessageType").textValue(),
                contentLength,
                vauDebugSC,
                vauDebugCS,
                Base64.getEncoder().encodeToString(message2Tree.get("AEAD_ct_key_confirmation").binaryValue())
            );
        }
    }
}
