package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.servicehealth.epa4all.server.rest.fileserver.WebDavProp;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.jugs.webdav.jaxrs.xml.elements.HRef;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.Prop;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;
import org.jugs.webdav.jaxrs.xml.elements.PropStat;
import org.jugs.webdav.jaxrs.xml.elements.Response;
import org.jugs.webdav.jaxrs.xml.elements.Status;
import org.jugs.webdav.jaxrs.xml.properties.CreationDate;
import org.jugs.webdav.jaxrs.xml.properties.DisplayName;
import org.jugs.webdav.jaxrs.xml.properties.GetLastModified;
import org.w3c.dom.Element;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.jugs.webdav.jaxrs.xml.properties.ResourceType.COLLECTION;

@ApplicationScoped
public class DirectoryProp implements WebDavProp {

    @Inject
    FileProp fileProp;

    @Override
    public MultiStatus propfind(
        File resource,
        PropFind propFind,
        URI requestUri,
        UriBuilder uriBuilder,
        int depth
    ) throws Exception {
        if (resource == null || !resource.isDirectory()) {
            return new MultiStatus();
        }
        PropStatInfo propStatInfo = buildDirectoryStat(resource, propFind);
        final Response davResource = new Response(
            new HRef(requestUri),
            null,
            null,
            null,
            propStatInfo.okStat,
            propStatInfo.notFoundStat
        );
        if (depth <= 0) {
            return new MultiStatus(davResource);
        } else {
            List<Response> nestedResources = new ArrayList<>();
            nestedResources.add(davResource);
            collectNestedResources(nestedResources, resource, uriBuilder, propFind, depth - 1);
            return new MultiStatus(nestedResources.toArray(Response[]::new));
        }
    }

    private static class PropStatInfo {
        PropStat okStat;
        PropStat[] notFoundStat;

        public PropStatInfo(PropStat okStat, PropStat[] notFoundStat) {
            this.okStat = okStat;
            this.notFoundStat = notFoundStat;
        }
    }

    // CreationDate,GetLastModified,GetContentLength,GetContentType,DisplayName
    
    private PropStatInfo buildDirectoryStat(File resource, PropFind propFind) {
        Date lastModified = new Date(resource.lastModified());
        Prop prop = new Prop(
            new CreationDate(lastModified),
            new GetLastModified(lastModified),
            new DisplayName(resource.getName()),
            COLLECTION
        );
        List<String> existingNames = getExistingNames(prop.getProperties());
        List<Object> missed = propFind.getProp().getProperties().stream()
            .filter(obj -> {
                if (obj instanceof Element element) {
                    return !existingNames.contains(element.getLocalName());
                } else {
                    return false;
                }
            })
            .toList();

        List<PropStat> propStats = new ArrayList<>();
        if (!missed.isEmpty()) {
            propStats.add(new PropStat(new Prop(missed), new Status(notFound())));
        }
        return new PropStatInfo(
            new PropStat(prop, new Status(okStatus())),
            propStats.toArray(PropStat[]::new)
        );
    }

    private List<String> getExistingNames(List<Object> existingProperties) {
        return existingProperties.stream()
            .filter(obj -> obj.getClass().isAnnotationPresent(XmlRootElement.class))
            .map(obj -> obj.getClass().getAnnotation(XmlRootElement.class).name())
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toList());
    }

    private void collectNestedResources(
        List<Response> nestedResources,
        File resource,
        UriBuilder uriBuilder,
        PropFind propFind,
        int depth
    ) throws Exception {
        File[] files = resource.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {

            String fileName = file.getName();

            // Date lastModified = new Date(file.lastModified());
            // PropStatBuilder propStatBuilder = new PropStatBuilder();
            // propStatBuilder
            //     .creationDate(lastModified)
            //     .lastModified(lastModified)
            //     .displayName(fileName)
            //     .status(okStatus());

            UriBuilder nestedBuilder = uriBuilder.clone().path(fileName);
            MultiStatus multiStatus = null;
            if (file.isDirectory()) {
                // propStatBuilder.isCollection();

                if (depth >= 0) {
                    multiStatus = propfind(file, propFind, nestedBuilder.build(), nestedBuilder, depth - 1);
                }
            } else {
                multiStatus = fileProp.propfind(file, propFind, nestedBuilder.build(), nestedBuilder, depth);

                // propStatBuilder.isResource(file.length(), "application/octet-stream"); // todo verify
            }

            if (multiStatus != null) {
                nestedResources.addAll(multiStatus.getResponses());
            }
        }
    }
}