package de.servicehealth.epa4all.server.rest;

import com.fasterxml.jackson.databind.JsonNode;
import de.servicehealth.api.epa4all.EpaAPI;
import de.servicehealth.vau.VauFacade;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static de.servicehealth.epa4all.server.cetp.mapper.Utils.getPayloads;
import static de.servicehealth.utils.ServerUtils.createObjectNode;
import static de.servicehealth.utils.ServerUtils.pretty;
import static de.servicehealth.vau.VauClient.VAU_CLIENT;
import static de.servicehealth.vau.VauClient.VAU_CLIENT_UUID;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.WILDCARD;
import static jakarta.ws.rs.core.Response.Status.OK;

@RequestScoped
@Path("{adminPath: vau-admin/.*}")
public class VauAdmin extends AbstractResource {

    @APIResponses({
        @APIResponse(responseCode = "200", description = "Refresh status"),
        @APIResponse(responseCode = "429", description = "duplicated call"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Produces(APPLICATION_JSON)
    @Operation(description = "Refresh VAU sessions")
    public Response refreshVauSessions(
        @PathParam("adminPath") String adminPath,
        @Context UriInfo uriInfo,
        @Parameter(name = X_BACKEND, description = "The target ePA backend of the affected VAU sessions")
        @QueryParam(X_BACKEND) String backend
    ) throws Exception {
        String query = uriInfo.getRequestUri().getQuery();
        Integer hash = Objects.hash(adminPath, query, backend);
        return deduplicatedCall(adminPath, query, hash, () -> {
            List<JsonNode> payloads = vauSessionsJob.refreshActiveVauSessions(backend);
            return Response.status(OK).entity(payloads).build();
        });
    }

    @APIResponses({
        @APIResponse(description = "ePA response"),
        @APIResponse(responseCode = "429", description = "duplicated call"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Consumes(WILDCARD)
    @Produces(WILDCARD)
    @Operation(summary = "Forward client's Admin GET request to ePA")
    public Response forward(
        @PathParam("adminPath") String adminPath,
        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @Parameter(name = VAU_CLIENT_UUID, description = "The identifier of the VAU session to reload, all sessions if skipped")
        @QueryParam(VAU_CLIENT_UUID) String vauClientUuid,
        @Parameter(name = X_BACKEND, description = "The target ePA backend of the affected VAU sessions")
        @QueryParam(X_BACKEND) String backend
    ) throws Exception {
        String query = uriInfo.getRequestUri().getQuery();
        Integer hash = Objects.hash(adminPath, query, vauClientUuid, backend);
        return deduplicatedCall(adminPath, query, hash, () ->
            forward(true, adminPath, uriInfo, httpHeaders, vauClientUuid, backend, null)
        );
    }

    private Response forward(
        boolean isGet,
        String adminPath,
        UriInfo uriInfo,
        HttpHeaders httpHeaders,
        String vauClientUuid,
        String backend,
        byte[] body
    ) {
        List<Response> responses = epaMultiService.getEpaBackendMap().entrySet().stream()
            .filter(e -> backend == null || e.getKey().equalsIgnoreCase(backend))
            .flatMap(e -> {
                try {
                    Map<String, String> xHeaders = prepareXHeaders(e.getKey(), Optional.empty());
                    String baseQuery = uriInfo.getRequestUri().getQuery();
                    EpaAPI epaApi = e.getValue();
                    VauFacade vauFacade = epaApi.getVauFacade();
                    return vauFacade.getSessionClients().stream()
                        .filter(vc -> vauClientUuid == null || vc.getUuid().equals(vauClientUuid))
                        .map(vc -> {
                            try {
                                return epaApi.getAdminProxy().forward(
                                    isGet, adminPath, baseQuery, vc.getUuid(), httpHeaders, xHeaders, body
                                );
                            } catch (Exception ex) {
                                Map<String, String> map = Map.of("error", ex.getMessage(), VAU_CLIENT, vc.getUuid());
                                JsonNode errorNode = createObjectNode(map);
                                return Response.status(Response.Status.CONFLICT).entity(errorNode).build();
                            }
                        });
                } catch (Exception ex) {
                    Map<String, String> map = Map.of("error", ex.getMessage(), VAU_CLIENT, String.valueOf(vauClientUuid));
                    JsonNode errorNode = createObjectNode(map);
                    Response response = Response.status(Response.Status.CONFLICT).entity(errorNode).build();
                    return Stream.of(response);
                }
            }).toList();

        return Response.status(OK).entity(pretty(getPayloads(responses))).build();
    }
}