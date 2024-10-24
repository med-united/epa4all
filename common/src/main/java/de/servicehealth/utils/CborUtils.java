package de.servicehealth.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;

public class CborUtils {

    private static final Logger log = LoggerFactory.getLogger(CborUtils.class);

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
