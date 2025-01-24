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
import jakarta.xml.bind.JAXBElement;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.servicehealth.epa4all.server.rest.fileserver.prop.MimeHelper.resolveMimeType;
import static de.servicehealth.utils.ServerUtils.asDate;
import static org.jugs.webdav.jaxrs.xml.properties.ResourceType.COLLECTION;

public abstract class AbstractWebDavProp implements WebDavProp {

    private static final Map<String, Function<PropSource, Object>> propSupplierMap;

    static  {
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
        propSupplierMap.put("birthday", propSource -> propSource.person != null
            ? new BirthDay(asDate(LocalDate.parse(propSource.person.getGeburtsdatum(), LOCALDATE_YYYYMMDD)))
            : null);
    }

    @Inject
    WebdavConfig webdavConfig;

    @Inject
    FolderService folderService;

    @Inject
    KonnektorClient konnektorClient;

    @Inject
    InsuranceDataService insuranceDataService;
    
    protected InsuranceData getInsuranceData(URI requestUri) {
        String path = requestUri.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        try {
            String[] parts = path.split("/");
            String telematikId = parts[1].trim();
            String kvnr = parts[2].trim();
            return insuranceDataService.getLocalInsuranceData(telematikId, kvnr);
        } catch (Exception e) {
            return null;
        }
    }

    private PropFind getDefaultLevelPropFind(File resource, URI requestUri, boolean directory) {
        Map<String, List<String>> availableProps = webdavConfig.getAvailableProps(directory);
        List<String> props = resolveLevelProps(availableProps, resource, requestUri);
        Prop prop = new Prop(props.toArray(Object[]::new));
        return new PropFind(prop);
    }

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
            levelPropFind = getDefaultLevelPropFind(resource, requestUri, directory);
        }
        InsuranceData insuranceData = getInsuranceData(requestUri);
        Map<String, List<String>> availableProps = webdavConfig.getAvailableProps(directory);
        PropStatInfo propStatInfo = levelPropFind.getPropName() != null
            ? getPropStatNamesInfo(availableProps, resource, requestUri)
            : getPropStatInfo(insuranceData, webdavConfig.getSmcbFolders(), availableProps, levelPropFind, resource, requestUri);

        return new MultiStatus(new Response(
            new HRef(requestUri),
            null,
            null,
            null,
            propStatInfo.okStat,
            propStatInfo.notFoundStat
        ));
    }

    @Override
    public PropStatInfo getPropStatInfo(
        InsuranceData insuranceData,
        Set<String> smcbFolders,
        Map<String, List<String>> availableProps,
        PropFind propFind,
        File resource,
        URI requestUri
    ) {
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person = null;
        if (insuranceData != null && insuranceData.getPersoenlicheVersichertendaten() != null) {
            UCPersoenlicheVersichertendatenXML.Versicherter versicherter = insuranceData.getPersoenlicheVersichertendaten().getVersicherter();
            person = versicherter.getPerson();
        }
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


        Date expiry = null;
        String smcb = null;
        int checksumsCount;
        if (insuranceData != null) {
            String telematikId = insuranceData.getTelematikId();
            String insurantId = insuranceData.getInsurantId();

            checksumsCount = folderService.getChecksums(telematikId, insurantId).size();
            try {
                long epochSecond = insuranceDataService.getEntitlementExpiry(telematikId, insurantId).getEpochSecond();
                expiry = new Date(epochSecond * 1000);
            } catch (Exception ignored) {
            }

            Optional<String> smcbOpt = konnektorClient.getTelematikMap().entrySet().stream()
                .filter(e -> e.getValue().equals(telematikId))
                .findFirst()
                .map(Map.Entry::getKey);
            if (smcbOpt.isPresent()) {
                smcb = smcbOpt.get();
            }
        } else {
            checksumsCount = 0;
        }

        List<String> props = resolveLevelProps(availableProps, resource, requestUri);
        PropSource propSource = new PropSource(resource, person, smcbFolders, checksumsCount, expiry, smcb);
        Prop prop = new Prop(props.stream()
            .filter(targetProps::contains)
            .map(propSupplierMap::get)
            .filter(Objects::nonNull)
            .map(f -> f.apply(propSource))
            .filter(Objects::nonNull)
            .toArray(Object[]::new));

        List<String> existingNames = getExistingNames(prop.getProperties());
        Object[] missed = propFind.getProp().getProperties().stream()
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

        List<PropStat> propStats = new ArrayList<>();
        if (missed.length > 0) {
            propStats.add(new PropStat(new Prop(missed), new Status(notFound())));
        }
        return new PropStatInfo(
            new PropStat(prop, new Status(okStatus())),
            propStats.toArray(PropStat[]::new)
        );
    }
}
