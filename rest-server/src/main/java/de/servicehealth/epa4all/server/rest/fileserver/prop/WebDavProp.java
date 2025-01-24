package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.servicehealth.epa4all.server.insurance.InsuranceData;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.Prop;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;
import org.jugs.webdav.jaxrs.xml.elements.PropStat;
import org.jugs.webdav.jaxrs.xml.elements.Status;

import javax.xml.namespace.QName;
import java.io.File;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface WebDavProp {



    DateTimeFormatter LOCALDATE_YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    DateTimeFormatter LOCALDATE_YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    SimpleDateFormat DATE_YYYY_MM_DD = new SimpleDateFormat("yyyy-MM-dd");

    MultiStatus propfind(
        File resource,
        PropFind propFind,
        URI requestUri,
        UriBuilder uriBuilder,
        int depth
    ) throws Exception;

    default Response.StatusType okStatus() {
        return Response.Status.OK;
    }

    default Response.StatusType notFound() {
        return Response.Status.NOT_FOUND;
    }

    List<String> resolveLevelProps(Map<String, List<String>> availableProps, File resource, URI requestUri);

    default int getLevel(URI requestUri) {
        return Arrays.stream(requestUri.getPath().split("/")).filter(s -> !s.isEmpty()).toList().size() - 1;
    }

    default PropStatInfo getPropStatNamesInfo(Map<String, List<String>> availableProps, File resource, URI requestUri) {
        List<String> props = resolveLevelProps(availableProps, resource, requestUri);
        Prop prop = new Prop(props.stream()
            .map(p -> new JAXBElement<>(new QName("", p), String.class, ""))
            .toArray(Object[]::new));
        PropStat okStat = new PropStat(prop, new Status(okStatus()));
        PropStat[] notFoundStat = new PropStat[0];
        return new PropStatInfo(okStat, notFoundStat);
    }

    PropStatInfo getPropStatInfo(
        InsuranceData insuranceData,
        Set<String> smcbFolders,
        Map<String, List<String>> availableProps,
        PropFind propFind,
        File resource,
        URI requestUri
    );

    default List<String> getExistingNames(List<Object> existingProperties) {
        return existingProperties.stream()
            .filter(obj -> obj.getClass().isAnnotationPresent(XmlRootElement.class))
            .map(obj -> obj.getClass().getAnnotation(XmlRootElement.class).name())
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toList());
    }
}
