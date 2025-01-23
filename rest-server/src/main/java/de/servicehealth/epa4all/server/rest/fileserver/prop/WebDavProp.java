package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.BirthDay;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.FirstName;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.LastName;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.Prop;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;
import org.jugs.webdav.jaxrs.xml.elements.PropStat;
import org.jugs.webdav.jaxrs.xml.elements.Status;
import org.jugs.webdav.jaxrs.xml.properties.CreationDate;
import org.jugs.webdav.jaxrs.xml.properties.DisplayName;
import org.jugs.webdav.jaxrs.xml.properties.GetContentLength;
import org.jugs.webdav.jaxrs.xml.properties.GetContentType;
import org.jugs.webdav.jaxrs.xml.properties.GetLastModified;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.io.File;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static jakarta.ws.rs.core.MediaType.TEXT_HTML;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static jakarta.ws.rs.core.MediaType.WILDCARD;
import static org.jugs.webdav.jaxrs.xml.properties.ResourceType.COLLECTION;

public interface WebDavProp {

    DateTimeFormatter LOCALDATE_YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    DateTimeFormatter LOCALDATE_YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    SimpleDateFormat DATE_YYYY_MM_DD = new SimpleDateFormat("yyyy-MM-dd");

    String APPLICATION_PDF = "application/pdf";

    Map<String, Set<String>> MIME_MAP = Map.of(
        TEXT_PLAIN, Set.of(".txt", ".log"),
        TEXT_HTML, Set.of(".htm", ".html"),
        APPLICATION_XML, Set.of(".xml", ".xhtml"),
        APPLICATION_OCTET_STREAM, Set.of(".bin"),
        APPLICATION_JSON, Set.of(".json"),
        APPLICATION_PDF, Set.of(".pdf")
    );

    Map<String, Function<PropSource, Object>> propSupplierMap = Map.of(
        "creationdate", propSource -> new CreationDate(new Date(propSource.file.lastModified())),
        "getlastmodified", propSource -> new GetLastModified(new Date(propSource.file.lastModified())),
        "displayname", propSource -> new DisplayName(propSource.file.getName()),
        "getcontenttype", propSource -> new GetContentType(resolveMimeType(propSource.file.getName())),
        "getcontentlength", propSource -> new GetContentLength(propSource.file.length()),
        "resourcetype", propSource -> COLLECTION,
        "firstname", propSource -> propSource.person != null ? new FirstName(propSource.person.getVorname()) : null,
        "lastname", propSource -> propSource.person != null ? new LastName(propSource.person.getNachname()) : null,
        "birthday", propSource -> propSource.person != null
            ? new BirthDay(asDate(LocalDate.parse(propSource.person.getGeburtsdatum(), LOCALDATE_YYYYMMDD)))
            : null
    );

    static Date asDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    }

    MultiStatus propfind(File resource, PropFind propFind, URI requestUri, UriBuilder uriBuilder, int depth) throws Exception;

    static String resolveMimeType(String fileName) {
        return MIME_MAP.entrySet().stream()
            .filter(e -> e.getValue().stream().anyMatch(fileName::endsWith))
            .findFirst()
            .map(Map.Entry::getKey).orElse(WILDCARD);
    }

    default Response.StatusType okStatus() {
        return Response.Status.OK;
    }

    default Response.StatusType notFound() {
        return Response.Status.NOT_FOUND;
    }

    default PropStatInfo getPropStatNamesInfo(List<String> props) {
        Object[] array = props.stream()
            .map(p -> new JAXBElement<>(new QName("", p), String.class, ""))
            .toArray(Object[]::new);
        return new PropStatInfo(
            new PropStat(new Prop(array), new Status(okStatus())),
            new PropStat[0]
        );
    }

    default PropStatInfo getPropStatInfo(List<String> props, InsuranceData insuranceData, File resource, PropFind propFind) {
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person = null;
        if (insuranceData != null && insuranceData.getPersoenlicheVersichertendaten() != null) {
            UCPersoenlicheVersichertendatenXML.Versicherter versicherter = insuranceData.getPersoenlicheVersichertendaten().getVersicherter();
            person = versicherter.getPerson();
        }
        Set<String> targetProps = propFind.getProp().getProperties().stream().map(obj -> {
                if (obj instanceof Element element) {
                    return element.getLocalName();
                } else {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        PropSource propSource = new PropSource(resource, person);
        Prop prop = new Prop(props.stream()
            .filter(targetProps::contains)
            .map(propSupplierMap::get)
            .filter(Objects::nonNull)
            .map(f -> f.apply(propSource))
            .filter(Objects::nonNull)
            .toArray(Object[]::new));


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

    default List<String> getExistingNames(List<Object> existingProperties) {
        return existingProperties.stream()
            .filter(obj -> obj.getClass().isAnnotationPresent(XmlRootElement.class))
            .map(obj -> obj.getClass().getAnnotation(XmlRootElement.class).name())
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toList());
    }
}
