package de.servicehealth.epa4all.server.rest.xds;

import de.servicehealth.epa4all.server.filetracker.delete.FileDelete;
import de.servicehealth.epa4all.server.rest.EpaContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.xml.bind.JAXBElement;
import oasis.names.tc.ebxml_regrep.xsd.lcm._3.RemoveObjectsRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.IdentifiableType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.InternationalStringType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.servicehealth.epa4all.server.filetracker.upload.soap.RawSoapUtils.deserializeRemoveElement;
import static de.servicehealth.epa4all.server.rest.xds.XdsResource.XDS_DOCUMENT_PATH;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

@SuppressWarnings("unused")
@RequestScoped
@Path(XDS_DOCUMENT_PATH)
public class Delete extends XdsResource {

    @APIResponses({
        @APIResponse(responseCode = "200", description = "Task uuid"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Consumes(MediaType.MEDIA_TYPE_WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    @Path("delete/raw")
    @Operation(summary = "Delete documents from the XDS registry")
    public String delete(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = KVNR, description = "Patient KVNR", required = true)
        @QueryParam(KVNR) String kvnr,
        @Parameter(description = "ns4:RemoveObjectsRequest XML element to submit to the XDS registry")
        InputStream is
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);

        String xmlElement = new String(is.readAllBytes(), ISO_8859_1);
        RemoveObjectsRequest request = deserializeRemoveElement(xmlElement);

        Set<String> uuids = request.getObjectRefList().getObjectRef().stream()
            .map(IdentifiableType::getId)
            .collect(Collectors.toSet());

        AdhocQueryResponse adhocQueryResponse = getAdhocQueryResponse(kvnr, epaContext);
        Set<String> fileNames = getFileNames(uuids, adhocQueryResponse);
        String taskId = UUID.randomUUID().toString();
        FileDelete fileDelete = new FileDelete(
            epaContext,
            taskId,
            fileNames.isEmpty() ? null : String.join(",", fileNames),
            telematikId,
            kvnr,
            request
        );
        fileActionEvent.fireAsync(fileDelete);
        return taskId;
    }

    private Set<String> getFileNames(Set<String> uuids, AdhocQueryResponse adhocQueryResponse) {
        List<JAXBElement<? extends IdentifiableType>> jaxbElements = adhocQueryResponse.getRegistryObjectList().getIdentifiable();
        return jaxbElements.stream().flatMap(e -> {
            ExtrinsicObjectType extrinsicObject = (ExtrinsicObjectType) e.getValue();

            Set<String> fileNames = new HashSet<>();
            String id = extrinsicObject.getId();
            InternationalStringType name = extrinsicObject.getName();
            if (uuids.contains(id)) {
                fileNames.add(name.getLocalizedString().getFirst().getValue());

                Optional<SlotType1> fileNameOpt = extrinsicObject.getSlot().stream().filter(s -> s.getName().equals("URI")).findFirst();
                fileNameOpt.ifPresent(slotType1 -> fileNames.add(slotType1.getValueList().getValue().getFirst()));
            }
            return fileNames.stream();
        }).collect(Collectors.toSet());
    }
}