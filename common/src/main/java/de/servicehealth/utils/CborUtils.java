package de.servicehealth.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;

import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CborUtils {

    private static final java.util.logging.Logger log = Logger.getLogger(CborUtils.class.getName());

    public static void printCborMessage(
        boolean m2,
        byte[] message,
        String vauCid,
        String vauDebugSC,
        String vauDebugCS,
        String contentLength
    ) {
        try {
            JsonNode message2Tree = new CBORMapper().readTree(message);
            if (vauCid != null) {
                log.info(String.format(
                    "VAU MESSAGE %s, length [%s]\nVAU-CID: %s\nVAU-DEBUG-S_K1_s2c: %s\nVAU-DEBUG-S_K1_c2s: %s\nKyber768_ct: %s\nAEAD_ct: %s\n\nECDH_ct: %s",
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
        } catch (Exception ex) {
            String messageType = m2 ? "M2" : "M4";
            log.log(Level.SEVERE, String.format("[%s] CBOR message is corrupted: %s", messageType, new String(message)));
        }
    }
}
