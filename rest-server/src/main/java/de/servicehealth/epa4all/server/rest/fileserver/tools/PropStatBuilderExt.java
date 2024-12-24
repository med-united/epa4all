package de.servicehealth.epa4all.server.rest.fileserver.tools;

import jakarta.ws.rs.core.Response;
import org.jugs.webdav.jaxrs.xml.elements.Prop;
import org.jugs.webdav.jaxrs.xml.elements.PropStat;
import org.jugs.webdav.jaxrs.xml.elements.Status;
import org.jugs.webdav.jaxrs.xml.properties.CreationDate;
import org.jugs.webdav.jaxrs.xml.properties.DisplayName;
import org.jugs.webdav.jaxrs.xml.properties.GetContentLength;
import org.jugs.webdav.jaxrs.xml.properties.GetContentType;
import org.jugs.webdav.jaxrs.xml.properties.GetLastModified;
import org.jugs.webdav.jaxrs.xml.properties.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class PropStatBuilderExt {

    private static final Logger log = LoggerFactory.getLogger(PropStatBuilderExt.class);
    private final List<Object> properties;
    private Response.Status status;
    private final Set<String> names;

    public PropStatBuilderExt() {
        properties = new LinkedList<>();
        names = new HashSet<>();
    }

    public PropStatBuilderExt creationDate(Date dateTime) {
        if (!names.contains("creationdate")) {
            CreationDate create = new CreationDate(dateTime);
            properties.add(create);
            names.add("creationdate");
        }
        return this;
    }

    public PropStatBuilderExt lastModified(Date dateTime) {
        if (!names.contains("getlastmodified")) {
            GetLastModified lastModified = new GetLastModified(dateTime);
            properties.add(lastModified);
            names.add("getlastmodified");
        }

        return this;
    }

    public PropStatBuilderExt contentLength(long length) {
        if (!names.contains("getcontentlength")) {
            GetContentLength contentLength = new GetContentLength(length);
            properties.add(contentLength);
            names.add("getcontentlength");
        }

        return this;
    }

    public PropStatBuilderExt isResource(long length, String mime) {
        if (!names.contains("getcontenttype")) {
            GetContentType type = new GetContentType(mime);
            properties.add(type);
            names.add("getcontenttype");
            GetContentLength contentLength = new GetContentLength(length);
            properties.add(contentLength);
            names.add("getcontentlength");
        }

        return this;
    }

    public PropStatBuilderExt isCollection() {
        if (!names.contains("resourcetype")) {
            ResourceType type = ResourceType.COLLECTION;
            properties.add(type);
            names.add("resourcetype");
        }

        return this;
    }

    public PropStatBuilderExt displayName(String displayName) {
        if (!names.contains("displayname")) {
            DisplayName name = new DisplayName(displayName);
            properties.add(name);
            names.add("displayname");
        }

        return this;
    }

    public PropStat notFound(Prop allprops) {
        boolean empty = true;
        List<Object> notFound = new ArrayList<>();
        for (Object prop : allprops.getProperties()) {
            if (prop instanceof Element element) {
                String name = element.getLocalName();
                if (!names.contains(name)) {
                    notFound.add(prop);
                    empty = false;
                }
            } else {
                log.debug("notfound-object - transformed into: {}", prop.getClass().getSimpleName());
            }
        }

        PropStat stat = null;
        if (!empty) {
            Object[] objects = notFound.toArray(new Object[properties.size()]);
            Prop prop = new Prop(objects);
            stat = new PropStat(prop, new Status(Response.Status.NOT_FOUND));
        }

        return stat;
    }

    public PropStatBuilderExt status(Response.Status status) {
        this.status = status;

        return this;
    }

    public PropStat build() {
        Object[] objects = properties.toArray(new Object[properties.size()]);
        Prop prop = new Prop(objects);
        return new PropStat(prop, new Status(status));
    }
}
