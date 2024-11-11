package de.servicehealth.epa4all.server.rest.fileserver;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.rest.fileserver.tools.PropStatBuilderExt;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Providers;
import org.jugs.webdav.jaxrs.xml.elements.HRef;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.Prop;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;
import org.jugs.webdav.jaxrs.xml.elements.PropStat;
import org.jugs.webdav.jaxrs.xml.elements.Status;
import org.jugs.webdav.jaxrs.xml.properties.CreationDate;
import org.jugs.webdav.jaxrs.xml.properties.GetLastModified;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import static jakarta.ws.rs.core.MediaType.APPLICATION_XML_TYPE;
import static org.jugs.webdav.jaxrs.xml.properties.ResourceType.COLLECTION;

public class DirectoryResource extends AbstractResource {

    private static final Logger log = Logger.getLogger(DirectoryResource.class.getName());

    private final String davFolder;

    public DirectoryResource(String davFolder, File resource, String url) {
        super(davFolder, resource, url);
        this.davFolder = davFolder;
    }

    @Override
    public Response move(final UriInfo uriInfo, String overwriteStr, String destination) throws URISyntaxException {
        logRequest("MOVE", uriInfo);
        URI uri = uriInfo.getBaseUri();
        String host = uri.getScheme() + "://" + uri.getHost() + "/" + WebdavConfig.RESOURCE_NAME + "/";
        String originalDestination = destination;
        destination = URLDecoder.decode(destination, StandardCharsets.UTF_8);
        destination = destination.replace(host, "");

        File destFile = new File(davFolder + File.separator + destination);
        boolean overwrite = overwriteStr.equalsIgnoreCase("T");

        return logResponse("MOVE", uriInfo, move(originalDestination, destFile, overwrite));
    }

    private Response move(String originalDestination, File destFile, boolean overwrite)
        throws URISyntaxException {
        if (destFile.equals(resource)) {
            return Response.status(403).build();
        } else {
            if (destFile.exists() && !overwrite) {
                return Response.status(Response.Status.PRECONDITION_FAILED).build();
            }
            if (!destFile.exists() || overwrite) {
                destFile.delete();
                boolean moved = resource.renameTo(destFile);
                if (moved)
                    return Response.created(new URI(originalDestination)).build();
                else
                    return Response.serverError().build();
            }
            return Response.status(409).build();
        }
    }

    @Override
    public Response propfind(final UriInfo uriInfo, final String depth, final InputStream entityStream, final long contentLength, final Providers providers, final HttpHeaders httpHeaders) throws IOException {
        logRequest("PROPFIND", uriInfo);
        if (!resource.exists()) {
            return logResponse("PROPFIND", uriInfo, Response.status(404).build());
        }

        Prop prop = null;
        if (contentLength > 0) {
            final MessageBodyReader<PropFind> reader = providers.getMessageBodyReader(PropFind.class, PropFind.class, new Annotation[0], APPLICATION_XML_TYPE);
            final PropFind entity = reader.readFrom(
                PropFind.class,
                PropFind.class,
                new Annotation[0],
                APPLICATION_XML_TYPE, httpHeaders.getRequestHeaders(),
                entityStream
            );
            prop = entity.getProp();
        }

        Date lastModified = new Date(resource.lastModified());
        final org.jugs.webdav.jaxrs.xml.elements.Response davResource = new org.jugs.webdav.jaxrs.xml.elements.Response(
            new HRef(uriInfo.getRequestUri()),
            null,
            null,
            null,
            new PropStat(
                new Prop(
                    new CreationDate(lastModified),
                    new GetLastModified(lastModified),
                    COLLECTION
                ),
                new Status(Response.Status.OK)
            )
        );

        return logResponse("PROPFIND", uriInfo, propfind(uriInfo, depth, prop, davResource));
    }

    private Response propfind(UriInfo uriInfo, String depth, Prop prop, org.jugs.webdav.jaxrs.xml.elements.Response davResource) {
        if ("0".equals(depth)) {
            return Response.ok(new MultiStatus(davResource)).build();
        }
        if (resource != null && resource.isDirectory()) {
            List<org.jugs.webdav.jaxrs.xml.elements.Response> responses = getResponses(uriInfo.getRequestUriBuilder(), prop, davResource, depth);

            MultiStatus st = new MultiStatus(
                responses.toArray(
                    new org.jugs.webdav.jaxrs.xml.elements.Response[responses.size()]
                )
            );
            return Response.ok(st).build();
        }
        return Response.noContent().build();
    }

	public List<org.jugs.webdav.jaxrs.xml.elements.Response> getResponses(UriBuilder uriBuilder, Prop prop,
			org.jugs.webdav.jaxrs.xml.elements.Response davResource, String depth) {
		Date lastModified;
		File[] files = resource.listFiles();
		List<org.jugs.webdav.jaxrs.xml.elements.Response> responses = new ArrayList<>();
		responses.add(davResource);
		for (File file : files) {
		    org.jugs.webdav.jaxrs.xml.elements.Response davFile;

		    lastModified = new Date(file.lastModified());
		    String fileName = file.getName();
		    PropStatBuilderExt props = new PropStatBuilderExt();
		    props.lastModified(lastModified).creationDate(lastModified).displayName(fileName).status(Response.Status.OK);

		    PropStat found = props.build();
		    PropStat notFound = null;
		    if (prop != null) {
//					props.isHidden(false);
//					props.lastAccessed(lastModified);
		    	notFound = props.notFound(prop);
		    }
		    
		    if (notFound != null)
		    	davFile = new org.jugs.webdav.jaxrs.xml.elements.Response(
		    			new HRef(uriBuilder.path(fileName).build()),
		    			null, null, null, found, notFound
		    			);
		    else
		    	davFile = new org.jugs.webdav.jaxrs.xml.elements.Response(
		    			new HRef(uriBuilder.path(fileName).build()),
		    			null, null, null, found
		    			);
		    if (file.isDirectory()) {
		        props.isCollection();
		        if("Infinity".equals(depth)) {
		        	DirectoryResource directoryResource = new DirectoryResource(rootFolder+"/"+fileName, file, url+"/"+fileName);
		        	responses.addAll(directoryResource.getResponses(uriBuilder.path(fileName), prop, davFile, depth));
		        } else {
		        	responses.add(davFile);
		        }
		    } else {
		        props.isResource(file.length(), "application/octet-stream");
		        responses.add(davFile);
		    }
		}
		return responses;
	}
}
