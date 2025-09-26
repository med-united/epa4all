package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.servicehealth.epa4all.server.jcr.prop.JcrProp;
import de.servicehealth.epa4all.server.propsource.PropBuilder;
import de.servicehealth.epa4all.server.propsource.PropSource;
import de.servicehealth.epa4all.server.rest.fileserver.paging.SortBy;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.jugs.webdav.jaxrs.xml.elements.HRef;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.Prop;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;
import org.jugs.webdav.jaxrs.xml.elements.PropStat;
import org.jugs.webdav.jaxrs.xml.elements.Response;
import org.jugs.webdav.jaxrs.xml.elements.ResponseDescription;
import org.jugs.webdav.jaxrs.xml.elements.Status;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static de.servicehealth.utils.ServerUtils.getPathParts;

public abstract class AbstractProp implements WebDavProp {

    @Inject
    PropBuilder propBuilder;

    private PropStatInfo getPropStatNamesInfo(File resource, URI requestUri) {
        List<String> props = resolveLevelProps(resource, requestUri);
        Prop prop = new Prop(props.stream()
            .map(p -> new JAXBElement<>(new QName("", p), String.class, ""))
            .toArray(Object[]::new));
        PropStat okStat = new PropStat(prop, new Status(okStatus()));
        PropStat[] notFoundStat = new PropStat[0];
        return new PropStatInfo(okStat, notFoundStat);
    }

    protected abstract List<String> resolveLevelProps(File resource, URI requestUri);

    protected MultiStatus buildDavResponseStatus(
        File resource,
        URI requestUri,
        PropFind propFind,
        boolean directory,
        SortBy sortBy
    ) {
        if (resource == null || (directory && !resource.isDirectory())) {
            return new MultiStatus();
        }
        PropFind levelPropFind = propFind;
        if (levelPropFind == null) {
            List<String> props = resolveLevelProps(resource, requestUri);
            Prop prop = new Prop(props.toArray(Object[]::new));
            levelPropFind = new PropFind(prop);
        }

        PropStatInfo propStatInfo = levelPropFind.getPropName() != null
            ? getPropStatNamesInfo(resource, requestUri)
            : getPropStatInfo(levelPropFind, resource, requestUri);

        long lastModified = propBuilder.getLastModified(resource, sortBy);
        return new MultiStatus(new Response(
            new HRef(requestUri),
            null,
            new ResponseDescription(String.valueOf(lastModified)),
            null,
            propStatInfo.okStat,
            propStatInfo.notFoundStat
        ));
    }

    PropStatInfo getPropStatInfo(@NotNull PropFind propFind, File resource, URI requestUri) {
        Set<String> targetProps = propFind.getProp().getProperties().stream().map(obj -> {
                if (obj instanceof Element element) {
                    return element.getLocalName();
                } else if (obj instanceof String s) {
                    return s;
                } else {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        List<String> pathParts = getPathParts(requestUri.getPath());
        PropSource propSource = propBuilder.buildPropSource(resource, pathParts);
        List<String> props = resolveLevelProps(resource, requestUri);
        Prop prop = new Prop(props.stream()
            .filter(targetProps::contains)
            .map(p -> propBuilder.getPropSupplierMap().get(JcrProp.valueOf(p)))
            .filter(Objects::nonNull)
            .map(f -> f.apply(propSource))
            .filter(Objects::nonNull)
            .toArray(Object[]::new));

        Object[] missed = getMissedProperties(propFind, prop);
        List<PropStat> propStats = new ArrayList<>();
        if (missed.length > 0) {
            propStats.add(new PropStat(new Prop(missed), new Status(notFound())));
        }
        return new PropStatInfo(
            new PropStat(prop, new Status(okStatus())),
            propStats.toArray(PropStat[]::new)
        );
    }

    private Object[] getMissedProperties(PropFind propFind, Prop prop) {
        List<String> existingNames = getExistingNames(prop.getProperties());
        return propFind.getProp().getProperties().stream()
            .map(obj -> {
                if (obj instanceof Element element) {
                    return element.getLocalName();
                } else if (obj instanceof String s) {
                    return s;
                } else {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .filter(n -> !existingNames.contains(n))
            .map(p -> new JAXBElement<>(new QName("", p), String.class, ""))
            .toArray(Object[]::new);
    }

    private List<String> getExistingNames(List<Object> existingProperties) {
        return existingProperties.stream()
            .filter(obj -> obj.getClass().isAnnotationPresent(XmlRootElement.class))
            .map(obj -> obj.getClass().getAnnotation(XmlRootElement.class).name())
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toList());
    }
}
