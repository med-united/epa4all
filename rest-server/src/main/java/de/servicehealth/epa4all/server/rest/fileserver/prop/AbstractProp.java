package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.servicehealth.epa4all.server.cetp.KonnektorClient;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.BirthDay;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.Entries;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.EntryUUID;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.FirstName;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.LastName;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.Smcb;
import de.servicehealth.epa4all.server.rest.fileserver.prop.custom.ValidTo;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.servicehealth.epa4all.server.rest.fileserver.prop.MimeHelper.resolveMimeType;
import static de.servicehealth.utils.ServerUtils.asDate;
import static org.jugs.webdav.jaxrs.xml.properties.ResourceType.COLLECTION;

public abstract class AbstractProp implements WebDavProp {

    private static final Map<String, Function<PropSource, Object>> propSupplierMap;

    static {
        propSupplierMap = new HashMap<>();
        propSupplierMap.put("entries", propSource -> new Entries(propSource.checksumsCount));
        propSupplierMap.put("validto", propSource -> propSource.expiry != null ? new ValidTo(propSource.expiry) : null);
        propSupplierMap.put("smcb", propSource -> propSource.smcb != null ? new Smcb(propSource.smcb) : null);

        propSupplierMap.put("creationdate", propSource -> new CreationDate(new Date(propSource.file.lastModified())));
        propSupplierMap.put("getlastmodified", propSource -> new GetLastModified(new Date(propSource.file.lastModified())));
        propSupplierMap.put("displayname", propSource -> new DisplayName(propSource.file.getName()));
        propSupplierMap.put("getcontenttype", propSource -> new GetContentType(resolveMimeType(propSource.file.getName())));
        propSupplierMap.put("getcontentlength", propSource -> new GetContentLength(propSource.file.length()));
        propSupplierMap.put("resourcetype", propSource -> COLLECTION);

        propSupplierMap.put("entryuuid", propSource -> propSource.smcbFolders.stream()
            .filter(s -> s.split("_")[0].equals(propSource.file.getName()))
            .findFirst().map(s -> new EntryUUID(s.split("_")[1])).orElse(null));

        propSupplierMap.put("firstname", propSource -> propSource.person != null ? new FirstName(propSource.person.getVorname()) : null);
        propSupplierMap.put("lastname", propSource -> propSource.person != null ? new LastName(propSource.person.getNachname()) : null);
        propSupplierMap.put("birthday", propSource -> {
            if (propSource.person != null) {
                try {
                    return new BirthDay(asDate(LocalDate.parse(propSource.person.getGeburtsdatum(), LOCALDATE_YYYYMMDD)));
                } catch (Exception e) {
                    return null;
                }
            } else {
                return null;
            }
        });
    }

    @Inject
    WebdavConfig webdavConfig;

    @Inject
    FolderService folderService;

    @Inject
    KonnektorClient konnektorClient;

    @Inject
    InsuranceDataService insuranceDataService;

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
        boolean directory
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

        return new MultiStatus(new Response(
            new HRef(requestUri),
            null,
            null,
            null,
            propStatInfo.okStat,
            propStatInfo.notFoundStat
        ));
    }

    private PropStatInfo getPropStatInfo(@NotNull PropFind propFind, File resource, URI requestUri) {
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

        PropSource propSource = buildPropSource(resource, requestUri);
        List<String> props = resolveLevelProps(resource, requestUri);
        Prop prop = new Prop(props.stream()
            .filter(targetProps::contains)
            .map(propSupplierMap::get)
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

    protected List<String> getPathParts(URI requestUri) {
        return Arrays.stream(requestUri.getPath().split("/")).filter(s -> !s.isEmpty()).toList();
    }

    private PropSource buildPropSource(File resource, URI requestUri) {
        List<String> pathParts = getPathParts(requestUri);

        UCPersoenlicheVersichertendatenXML.Versicherter.Person person = null;
        String telematikId = pathParts.size() > 1 ? pathParts.get(1).trim() : null;
        String insurantId = pathParts.size() > 2 ? pathParts.get(2).trim() : null;
        InsuranceData insuranceData = insuranceDataService.getData(telematikId, insurantId);
        if (insuranceData != null && insuranceData.getPersoenlicheVersichertendaten() != null) {
            UCPersoenlicheVersichertendatenXML.Versicherter versicherter = insuranceData.getPersoenlicheVersichertendaten().getVersicherter();
            person = versicherter.getPerson();
        }
        Date expiry = null;
        try {
            expiry = Date.from(insuranceDataService.getEntitlementExpiry(telematikId, insurantId));
        } catch (Exception ignored) {
        }
        int checksumsCount = folderService.getChecksums(telematikId, insurantId).size();
        String smcb = konnektorClient.getTelematikMap().entrySet().stream()
            .filter(e -> e.getValue().equals(telematikId))
            .findFirst()
            .map(Map.Entry::getKey).orElse(null);
        return new PropSource(resource, person, webdavConfig.getSmcbFolders(), checksumsCount, expiry, smcb);
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
