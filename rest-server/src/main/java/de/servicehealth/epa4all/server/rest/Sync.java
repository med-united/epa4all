package de.servicehealth.epa4all.server.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RequestScoped
@Path("sync")
public class Sync extends AbstractResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("upload/{konnektor : ([0-9a-zA-Z\\-]+)?}")
    public String uploadAll(
        @HeaderParam("Lang-Code") String languageCode,
        @PathParam("konnektor") String konnektor,
        @QueryParam("kvnr") String kvnr
    ) {
        try {
            EpaContext epaContext = prepareEpaContext(kvnr);
            List<String> tasksIds = bulkUploader.uploadInsurantFiles(epaContext, telematikId, kvnr, languageCode);
            return String.join("\n", tasksIds);
        } catch (Exception e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }
}
