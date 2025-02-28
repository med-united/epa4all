package de.servicehealth.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import de.servicehealth.vau.GeneralError;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

import static de.servicehealth.logging.LogContext.MASK_SENSITIVE;
import static de.servicehealth.utils.ServerUtils.asString;
import static de.servicehealth.utils.ServerUtils.from;

public class CborUtils {

    private static final Logger log = LoggerFactory.getLogger(CborUtils.class.getName());

    private static final Config CONFIG = ConfigProvider.getConfig();
    private static final boolean maskSensitive = CONFIG.getOptionalValue(MASK_SENSITIVE, Boolean.class).orElse(true);

    public static String getGeneralErrorAsString(byte[] bytes) {
        try {
            JsonNode node = new CBORMapper().readTree(bytes);
            return asString(from(node, GeneralError.class));
        } catch (Exception e) {
            return null;
        }
    }

    public static void printCborM2(String vauCid, byte[] m2, String vauDebugSC, String vauDebugCS, String contentLength) {
        try {
            String m2Template = "VAU MESSAGE M2, length [%s]\nVAU-CID: %s\nVAU-DEBUG-S_K1_s2c: %s\nVAU-DEBUG-S_K1_c2s: %s\nKyber768_ct: %s\nAEAD_ct: %s\nECDH_ct: %s";
            if (maskSensitive) {
                log.info(String.format(m2Template, contentLength, vauCid, "XXX", "XXX", "XXX", "XXX", "XXX"));
            } else {
                JsonNode jsonNode = new CBORMapper().readTree(m2);
                log.info(String.format(m2Template,
                    contentLength,
                    vauCid,
                    vauDebugSC,
                    vauDebugCS,
                    Base64.getEncoder().encodeToString(jsonNode.get("Kyber768_ct").binaryValue()),
                    Base64.getEncoder().encodeToString(jsonNode.get("AEAD_ct").binaryValue()),
                    jsonNode.get("ECDH_ct").toString()
                ));
            }
        } catch (Exception e) {
            log.error(String.format("[M2] CBOR message is corrupted: %s", new String(m2)));
        }
    }

    public static void printCborM4(byte[] m4, String vauDebugSC, String vauDebugCS, String contentLength) {
        try {
            String m4Template = "VAU MESSAGE M4, length [%s]\nVAU-DEBUG-S_K2_s2c_INFO: %s\nVAU-DEBUG-S_K2_c2s_INFO: %s\nAEAD_ct_key_confirmation: %s";
            if (maskSensitive) {
                log.info(String.format(m4Template, contentLength, "XXX", "XXX", "XXX"));
            } else {
                JsonNode jsonNode = new CBORMapper().readTree(m4);
                log.info(String.format(m4Template,
                    contentLength,
                    vauDebugSC,
                    vauDebugCS,
                    Base64.getEncoder().encodeToString(jsonNode.get("AEAD_ct_key_confirmation").binaryValue())
                ));
            }
        } catch (Exception e) {
            log.error(String.format("[M4] CBOR message is corrupted: %s", new String(m4)));
        }
    }
}
