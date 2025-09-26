package de.servicehealth.epa4all.server.rest;

import de.servicehealth.epa4all.server.presription.AiConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
@Path("equipment")
public class Equipment {

    @Inject
    AiConfig aiConfig;

    HttpClient client = HttpClient.newHttpClient();

    @APIResponses({
        @APIResponse(responseCode = "200", description = "Search for medical equipment succeeded"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Return medical equipment list related to the search request")
    public String search(
        @Parameter(name = "q", description = "Search text", required = true)
        @NotBlank @QueryParam("q") String searchText
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("%s/equipment?q=%s".formatted(aiConfig.getSearchUrl(), URLEncoder.encode(searchText, UTF_8))))
            .header("Content-Type", APPLICATION_JSON)
            .GET()
            .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
