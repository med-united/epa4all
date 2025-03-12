package de.servicehealth.epa4all.server.propsource;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.servicehealth.epa4all.server.cetp.KonnektorClient;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.jcr.prop.JcrProp;
import de.servicehealth.epa4all.server.jcr.prop.custom.BirthDay;
import de.servicehealth.epa4all.server.jcr.prop.custom.Entries;
import de.servicehealth.epa4all.server.jcr.prop.custom.EntryUUID;
import de.servicehealth.epa4all.server.jcr.prop.custom.FirstName;
import de.servicehealth.epa4all.server.jcr.prop.custom.LastName;
import de.servicehealth.epa4all.server.jcr.prop.custom.Smcb;
import de.servicehealth.epa4all.server.jcr.prop.custom.ValidTo;
import de.servicehealth.epa4all.server.rest.fileserver.paging.SortBy;
import de.servicehealth.epa4all.server.rest.fileserver.prop.type.DirectoryType;
import de.servicehealth.epa4all.server.rest.fileserver.prop.type.FileType;
import de.servicehealth.folder.WebdavConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.jackrabbit.value.LongValue;
import org.jugs.webdav.jaxrs.xml.properties.CreationDate;
import org.jugs.webdav.jaxrs.xml.properties.DisplayName;
import org.jugs.webdav.jaxrs.xml.properties.GetContentLength;
import org.jugs.webdav.jaxrs.xml.properties.GetContentType;
import org.jugs.webdav.jaxrs.xml.properties.GetLastModified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Value;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.LOCALDATE_YYYYMMDD;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.birthday;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.creationdate;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.displayname;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.entries;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.entryuuid;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.firstname;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.getcontentlength;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.getcontenttype;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.getlastmodified;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.lastname;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.resourcetype;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.smcb;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.validto;
import static de.servicehealth.utils.MimeHelper.resolveMimeType;
import static de.servicehealth.utils.ServerUtils.asDate;
import static de.servicehealth.utils.ServerUtils.getPathParts;
import static java.util.stream.Collectors.toMap;
import static org.jugs.webdav.jaxrs.xml.properties.ResourceType.COLLECTION;

@ApplicationScoped
public class PropBuilder {

    private static final Logger log = LoggerFactory.getLogger(PropBuilder.class.getName());

    private static final Map<JcrProp, Function<PropSource, Object>> propSupplierMap;

    static {
        propSupplierMap = new HashMap<>();
        propSupplierMap.put(entries, propSource -> new Entries(propSource.getChecksumsCount()));
        propSupplierMap.put(validto, propSource -> propSource.getExpiry() != null ? new ValidTo(propSource.getExpiry()) : null);
        propSupplierMap.put(smcb, propSource -> propSource.getSmcb() != null ? new Smcb(propSource.getSmcb()) : null);

        propSupplierMap.put(creationdate, propSource -> new CreationDate(new Date(propSource.getFile().lastModified())));
        propSupplierMap.put(getlastmodified, propSource -> new GetLastModified(new Date(propSource.getFile().lastModified())));
        propSupplierMap.put(displayname, propSource -> new DisplayName(propSource.getFile().getName()));
        propSupplierMap.put(getcontenttype, propSource -> new GetContentType(resolveMimeType(propSource.getFile().getName())));
        propSupplierMap.put(getcontentlength, propSource -> new GetContentLength(propSource.getFile().length()));
        propSupplierMap.put(resourcetype, propSource -> COLLECTION);

        propSupplierMap.put(entryuuid, propSource -> propSource.getSmcbFolders().stream()
            .filter(s -> s.split("_")[0].equals(propSource.getFile().getName()))
            .findFirst().map(s -> new EntryUUID(s.split("_")[1])).orElse(null));

        propSupplierMap.put(firstname, propSource -> propSource.getPerson() != null ? new FirstName(propSource.getPerson().getVorname()) : null);
        propSupplierMap.put(lastname, propSource -> propSource.getPerson() != null ? new LastName(propSource.getPerson().getNachname()) : null);
        propSupplierMap.put(birthday, propSource -> {
            if (propSource.getPerson() != null) {
                try {
                    return new BirthDay(asDate(LocalDate.parse(propSource.getPerson().getGeburtsdatum(), LOCALDATE_YYYYMMDD)));
                } catch (Exception e) {
                    return null;
                }
            } else {
                return null;
            }
        });
    }

    @Inject
    InsuranceDataService insuranceDataService;

    @Inject
    FolderService folderService;

    @Inject
    KonnektorClient konnektorClient;

    @Inject
    WebdavConfig webdavConfig;

    public Map<JcrProp, Function<PropSource, Object>> getPropSupplierMap() {
        return propSupplierMap;
    }

    public PropSource buildPropSource(File resource, List<String> pathParts) {
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person = null;
        String telematikId = pathParts.size() > 1 ? pathParts.get(1).trim() : null;
        String insurantId = pathParts.size() > 2 ? pathParts.get(2).trim() : null;
        Set<String> smcbFolders = webdavConfig.getSmcbFolders();
        if (telematikId != null && insurantId != null) {
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
            String smcb = konnektorClient.getSmcbTelematikMap().entrySet().stream()
                .filter(e -> e.getValue().equals(telematikId))
                .findFirst()
                .map(Map.Entry::getKey).orElse(null);
            return new PropSource(resource, smcbFolders, person, checksumsCount, expiry, smcb);
        } else {
            return new PropSource(resource, smcbFolders, null, 0, null, null);
        }
    }

    public List<String> resolveFileProps(File resource) {
        Map<String, List<String>> availableProps = webdavConfig.getAvailableProps(false);
        Map<FileType, List<String>> fileTypeMap = availableProps.entrySet().stream().collect(toMap(
            e -> FileType.valueOf(e.getKey()), e -> e.getValue().stream().filter(s -> !s.isEmpty()).toList()
        ));
        List<String> props = new ArrayList<>(fileTypeMap.get(FileType.Mandatory));
        props.addAll(fileTypeMap.get(FileType.fromName(resource.getName())));
        return props;
    }

    public List<String> resolveDirectoryProps(int level) {
        Map<String, List<String>> availableProps = webdavConfig.getAvailableProps(true);
        Map<DirectoryType, List<String>> directoryTypeMap = availableProps.entrySet().stream().collect(toMap(
            e -> DirectoryType.valueOf(e.getKey()), e -> e.getValue().stream().filter(s -> !s.isEmpty()).toList()
        ));
        List<String> props = new ArrayList<>(directoryTypeMap.get(DirectoryType.Mandatory));
        directoryTypeMap.entrySet().stream()
            .filter(e -> e.getKey().getLevel() == level)
            .findFirst()
            .map(e -> e.getValue().stream().filter(s -> !s.isEmpty()).toList()).ifPresent(props::addAll);
        return props;
    }

    public long getLastModified(File resource, SortBy sortBy) {
        if (resource.isFile()) {
            return resource.lastModified();
        } else {
            List<File> sortedLeafFiles = folderService.getLeafFiles(resource).stream().sorted((f1, f2) ->
                switch (sortBy) {
                    case Latest -> -1 * Long.compare(f1.lastModified(), f2.lastModified());
                    case Earliest -> Long.compare(f1.lastModified(), f2.lastModified());
                }
            ).toList();
            return sortedLeafFiles.isEmpty()
                ? resource.lastModified()
                : sortedLeafFiles.getFirst().lastModified();
        }
    }

    public void setEpaProps(File file, Node node) {
        boolean directory = file.isDirectory();

        List<String> pathParts = getPathParts(file.getPath());
        pathParts = pathParts.subList(pathParts.indexOf("webdav"), pathParts.size());
        PropSource propSource = buildPropSource(file, pathParts);

        int level = pathParts.size() - 1;
        List<String> props = directory
            ? resolveDirectoryProps(level).stream().toList()
            : resolveFileProps(file);

        getPropSupplierMap().entrySet().stream()
            .filter(e -> props.contains(e.getKey().name()))
            .forEach(e -> {
                JcrProp jcrProp = e.getKey();
                Function<PropSource, Object> propFunc = e.getValue();
                Function<Object, Value> valueFunc = jcrProp.getValueFunc();
                try {
                    if (jcrProp == getlastmodified) {
                        long lastModified = getLastModified(file, SortBy.Latest);
                        node.setProperty(getlastmodified.epaName(), new LongValue(lastModified));
                    } else {
                        Object propResult = propFunc.apply(propSource);
                        if (propResult != null) {
                            Value value = valueFunc.apply(propResult);
                            node.setProperty(jcrProp.epaName(), value);
                        }
                    }
                } catch (Exception ex) {
                    String msg = String.format(
                        "Error while setting property: file=%s prop=%s",
                        file.getAbsolutePath(), jcrProp.epaName()
                    );
                    log.error(msg, ex);
                }
            });
    }
}
