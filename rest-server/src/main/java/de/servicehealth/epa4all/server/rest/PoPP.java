package de.servicehealth.epa4all.server.rest;

import static de.servicehealth.logging.LogContext.resultMdcEx;
import static jakarta.ws.rs.core.MediaType.WILDCARD;

import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.glassfish.jaxb.core.v2.TODO;

import de.servicehealth.api.epa4all.EpaAPI;
import de.servicehealth.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.vsd.VsdService;
import de.servicehealth.model.EntitlementRequestTypeV2;
import de.servicehealth.model.ValidToResponseType;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.impl.jose.JWT;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@SuppressWarnings("unused")
@RequestScoped
@Path("popp")
public class PoPP extends AbstractResource {

    @Inject
    protected EpaMultiService epaMultiService;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "The patient entitlement was successfully created"),
        @APIResponse(responseCode = "400", description = "x-insurantid is invalid"),
        @APIResponse(responseCode = "409", description = "ePA error"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Consumes(WILDCARD)
    @Produces(WILDCARD)
    @Path("token")
    @Operation(summary = "Set entitlement for KVNR using PNW")
    public Response setEntitlement(
        String poppToken
    ) throws Exception {
        String insurantId = extractInsurantId(poppToken).toString();
        EpaContext epaContext = prepareEpaContext(insurantId);
        EpaAPI epaAPI = epaMultiService.findEpaAPI(insurantId);
        EntitlementRequestTypeV2 entitlementRequestTypeV2 = new EntitlementRequestTypeV2();
        entitlementRequestTypeV2.setPopp(poppToken);
        ValidToResponseType validToResponseType = epaAPI.getEntitlementsAPI().setEntitlementPsV2(insurantId, epaMultiService.getEpaConfig().getEpaUserAgent(), entitlementRequestTypeV2, UUID.randomUUID());
        return Response.ok(validToResponseType).build();

    }
    // eyJraWQiOiI0SVZZSHk3MjFLMHJualo4XzlmbnNLb2ZzMGVLaEdPY3FFRFZvMFJCWkZRIiwidHlwIjoidm5kLnRlbGVtYXRpay5wb3BwK2p3dCIsImFsZyI6IkVTMjU2In0.eyJwcm9vZk1ldGhvZCI6ImVoYy1wcmFjdGl0aW9uZXItdHJ1c3RlZGNoYW5uZWwiLCJwYXRpZW50UHJvb2ZUaW1lIjoxNzc2NTQyOTI5LCJhY3RvcklkIjoidGVsZW1hdGlrLWlkIiwicGF0aWVudElkIjoiSzIxMDE0MDE1NSIsImF1dGhvcml6YXRpb25fZGV0YWlscyI6ImRldGFpbHMiLCJpc3MiOiJodHRwczovL3BvcHAuZXhhbXBsZS5jb20iLCJhY3RvclByb2Zlc3Npb25PaWQiOiIxLjIuMjc2LjAuNzYuNC41MCIsInZlcnNpb24iOiIxLjAuMCIsImlhdCI6MTc3NjU0MjkyOSwiaW5zdXJlcklkIjoiMTAyMTcxMDEyIn0.Uqh-NBl3O0jd-xeTR7N0ZLHGkqHo3XBOT-TVh0q8l3BrkBls6dtceZha-1RC2NOXpTkmnjAbAi8m7dlJUcGC-g
    private Object extractInsurantId(String poppToken) {
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
