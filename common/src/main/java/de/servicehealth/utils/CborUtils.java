package de.servicehealth.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

import static de.servicehealth.logging.LogContext.MASK_SENSITIVE;

public class CborUtils {

    private static final Logger log = LoggerFactory.getLogger(CborUtils.class.getName());

    private static final Config CONFIG = ConfigProvider.getConfig();
    private static final boolean maskSensitive = CONFIG.getOptionalValue(MASK_SENSITIVE, Boolean.class).orElse(true);

    public static void printCborMessage(
        boolean m2,
        byte[] message,
        String vauCid,
        String vauDebugSC,
        String vauDebugCS,
        String contentLength
    ) {
        String messageType = m2 ? "M2" : "M4";
        try {
            if (maskSensitive) {
                if (vauCid != null) {
                    log.info(String.format(
                        "VAU MESSAGE %s, length [%s]\nVAU-CID: %s\nVAU-DEBUG-S_K1_s2c: %s\nVAU-DEBUG-S_K1_c2s: %s\nKyber768_ct: %s\nAEAD_ct: %s\nECDH_ct: %s",
                        messageType,
                        contentLength,
                        vauCid,
                        "XXX",
                        "XXX",
                        "XXX",
                        "XXX",
                        "XXX"
                    ));
                } else {
                    log.info(String.format(
                        "VAU MESSAGE %s, length [%s]\nVAU-DEBUG-S_K2_s2c_INFO: %s\nVAU-DEBUG-S_K2_c2s_INFO: %s\nAEAD_ct_key_confirmation: %s",
                        messageType,
                        contentLength,
                        "XXX",
                        "XXX",
                        "XXX"
                    ));
                }
            } else {
                JsonNode message2Tree = new CBORMapper().readTree(message);
                if (vauCid != null) {
                    log.info(String.format(
                        "VAU MESSAGE %s, length [%s]\nVAU-CID: %s\nVAU-DEBUG-S_K1_s2c: %s\nVAU-DEBUG-S_K1_c2s: %s\nKyber768_ct: %s\nAEAD_ct: %s\nECDH_ct: %s",
                        message2Tree.get("MessageType").textValue(),
                        contentLength,
                        vauCid,
                        vauDebugSC,
                        vauDebugCS,
                        Base64.getEncoder().encodeToString(message2Tree.get("Kyber768_ct").binaryValue()),
                        Base64.getEncoder().encodeToString(message2Tree.get("AEAD_ct").binaryValue()),
                        message2Tree.get("ECDH_ct").toString()
                    ));
                } else {
                    log.info(String.format(
                        "VAU MESSAGE %s, length [%s]\nVAU-DEBUG-S_K2_s2c_INFO: %s\nVAU-DEBUG-S_K2_c2s_INFO: %s\nAEAD_ct_key_confirmation: %s",
                        message2Tree.get("MessageType").textValue(),
                        contentLength,
                        vauDebugSC,
                        vauDebugCS,
                        Base64.getEncoder().encodeToString(message2Tree.get("AEAD_ct_key_confirmation").binaryValue())
                    ));
                }
            }
        } catch (Exception ex) {
            log.error(String.format("[%s] CBOR message is corrupted: %s", messageType, new String(message)));
        }
    }
}
