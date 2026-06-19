package de.servicehealth.epa4all.server.rest.filter;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.impl.jose.JWT;

public class PoppUtils {

    public PoppUtils() {
    }

    // eyJraWQiOiI0SVZZSHk3MjFLMHJualo4XzlmbnNLb2ZzMGVLaEdPY3FFRFZvMFJCWkZRIiwidHlwIjoidm5kLnRlbGVtYXRpay5wb3BwK2p3dCIsImFsZyI6IkVTMjU2In0.eyJwcm9vZk1ldGhvZCI6ImVoYy1wcmFjdGl0aW9uZXItdHJ1c3RlZGNoYW5uZWwiLCJwYXRpZW50UHJvb2ZUaW1lIjoxNzc2NTQyOTI5LCJhY3RvcklkIjoidGVsZW1hdGlrLWlkIiwicGF0aWVudElkIjoiSzIxMDE0MDE1NSIsImF1dGhvcml6YXRpb25fZGV0YWlscyI6ImRldGFpbHMiLCJpc3MiOiJodHRwczovL3BvcHAuZXhhbXBsZS5jb20iLCJhY3RvclByb2Zlc3Npb25PaWQiOiIxLjIuMjc2LjAuNzYuNC41MCIsInZlcnNpb24iOiIxLjAuMCIsImlhdCI6MTc3NjU0MjkyOSwiaW5zdXJlcklkIjoiMTAyMTcxMDEyIn0.Uqh-NBl3O0jd-xeTR7N0ZLHGkqHo3XBOT-TVh0q8l3BrkBls6dtceZha-1RC2NOXpTkmnjAbAi8m7dlJUcGC-g
    public static String extractInsurantId(String poppToken) {
        // payload:
        // {
        // "proofMethod": "ehc-practitioner-trustedchannel",
        // "patientProofTime": 1776542929,
        // "actorId": "telematik-id",
        // "patientId": "K210140155",
        // "authorization_details": "details",
        // "iss": "https://popp.example.com",
        // "actorProfessionOid": "1.2.276.0.76.4.50",
        // "version": "1.0.0",
        // "iat": 1776542929,
        // "insurerId": "102171012"
        // }
        // parse poppToken and extract patientId (KVNR)
        JsonObject jwt = JWT.parse(poppToken);
        return jwt.getJsonObject("payload").getString("patientId");
    }
}
