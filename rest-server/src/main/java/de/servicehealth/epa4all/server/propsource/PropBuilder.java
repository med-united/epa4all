package de.servicehealth.epa4all.server.propsource;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.servicehealth.epa4all.server.cetp.KonnektorClient;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.jcr.JcrConfig;
import de.servicehealth.epa4all.server.jcr.mixin.Mixin;
import de.servicehealth.epa4all.server.jcr.mixin.MixinsProvider;
import de.servicehealth.epa4all.server.jcr.prop.MixinProp;
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

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static de.servicehealth.epa4all.server.filetracker.ChecksumFile.CHECKSUM_FILE_NAME;
import static de.servicehealth.epa4all.server.jcr.Workspace.ROOT_FOLDER_NODE;
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
import static de.servicehealth.folder.IFolderService.DS_STORE;
import static de.servicehealth.utils.MimeHelper.resolveMimeType;
import static de.servicehealth.utils.ServerUtils.asDate;
import static de.servicehealth.utils.ServerUtils.getPathParts;
import static java.util.stream.Collectors.toMap;
import static org.jugs.webdav.jaxrs.xml.properties.ResourceType.COLLECTION;

@ApplicationScoped
public class PropBuilder {

    private static final Logger log = LoggerFactory.getLogger(PropBuilder.class.getName());

    public static final String TYPE_NT_FOLDER = "nt:folder";
    public static final String TYPE_NT_FILE = "nt:file";
    public static final String TYPE_NT_RESOURCE = "nt:resource";

    public static final String PATH_JCR_CONTENT = "jcr:content";

    public static final Predicate<File> SKIPPED_FILES = file ->
        Set.of(
            CHECKSUM_FILE_NAME,
            DS_STORE
        ).stream().noneMatch(s -> s.equals(file.getName()));

    private static final Map<MixinProp, Function<PropSource, Object>> propSupplierMap;


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

        propSupplierMap.put(entryuuid, propSource -> propSource.getSmcbFolders().entrySet().stream()
            .filter(e -> e.getKey().equals(propSource.getFile().getName()))
            .findFirst()
            .map(e -> new EntryUUID(e.getValue()))
            .orElse(null));

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

    private final InsuranceDataService insuranceDataService;
    private final FolderService folderService;
    private final KonnektorClient konnektorClient;
    private final WebdavConfig webdavConfig;
    private final MixinsProvider mixinsProvider;
    private final JcrConfig jcrConfig;

    @Inject
    public PropBuilder(
        InsuranceDataService insuranceDataService,
        KonnektorClient konnektorClient,
        MixinsProvider mixinsProvider,
        FolderService folderService,
        WebdavConfig webdavConfig,
        JcrConfig jcrConfig
    ) {
        this.insuranceDataService = insuranceDataService;
        this.konnektorClient = konnektorClient;
        this.mixinsProvider = mixinsProvider;
        this.folderService = folderService;
        this.webdavConfig = webdavConfig;
        this.jcrConfig = jcrConfig;
    }

    public Map<MixinProp, Function<PropSource, Object>> getPropSupplierMap() {
        return propSupplierMap;
    }

    public PropSource buildPropSource(File resource, List<String> pathParts) {
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person = null;
        String telematikId = pathParts.size() > 1 ? pathParts.get(1).trim() : null;
        String insurantId = pathParts.size() > 2 ? pathParts.get(2).trim() : null;
        Map<String, String> smcbFolders = webdavConfig.getSmcbFolders();
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
            e -> FileType.valueOf(e.getKey()), Map.Entry::getValue
        ));
        List<String> props = new ArrayList<>(fileTypeMap.get(FileType.Mandatory));
        props.addAll(fileTypeMap.get(FileType.fromName(resource.getName())));
        return props;
    }

    public List<String> resolveDirectoryProps(int level) {
        Map<String, List<String>> availableProps = webdavConfig.getAvailableProps(true);
        Map<DirectoryType, List<String>> directoryTypeMap = availableProps.entrySet().stream().collect(toMap(
            e -> DirectoryType.valueOf(e.getKey()), Map.Entry::getValue
        ));
        List<String> props = new ArrayList<>(directoryTypeMap.get(DirectoryType.Mandatory));
        directoryTypeMap.entrySet().stream()
            .filter(e -> e.getKey().getLevel() == level)
            .findFirst()
            .map(e -> e.getValue()
                .stream()
                .filter(s -> !s.isEmpty())
                .filter(s -> !s.equals("root"))
                .toList()
            ).ifPresent(props::addAll);
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

    private Node addNodeAndApplyMixins(Node parentNode, String relPath, String primaryType, File file) throws RepositoryException {
        Node node = parentNode.addNode(relPath, primaryType);
        applyMixins(node, primaryType, file);
        return node;
    }

    public Node handleFolder(Session session, Node parentNode, File file, String fileName, boolean staleMixins) throws RepositoryException {
        try {
            Node dirNode = parentNode.getNode(fileName);
            // it is implied that all orphan mixin properties are already removed in TypeService
            if (staleMixins) {
                applyMixins(dirNode, TYPE_NT_FOLDER, file);
                session.save();
            }
            return dirNode;
        } catch (PathNotFoundException e) {
            Node node = addNodeAndApplyMixins(parentNode, fileName, TYPE_NT_FOLDER, file);
            session.save();
            return node;
        }
    }

    public void handleFileDeletion(Session session, Node parentNode, File file) throws RepositoryException {
        try {
            Node node = parentNode.getNode(file.getName());
            node.remove();
            session.save();
        } catch (PathNotFoundException e) {
            log.warn("JCR node for file '%s' is not found", e);
            session.refresh(false);
        }
    }

    public void handleFileCreation(Session session, Node parentNode, File file, boolean staleMixins) throws RepositoryException {
        try {
            Node fileNode = parentNode.getNode(file.getName());
            // it is implied that all orphan mixin properties are already removed in TypeService
            if (staleMixins) {
                applyMixins(fileNode, TYPE_NT_FILE, file);
                session.save();
            }
        } catch (PathNotFoundException e) {
            Node fileNode = addNodeAndApplyMixins(parentNode, file.getName(), TYPE_NT_FILE, file);
            Node contentNode = addNodeAndApplyMixins(fileNode, PATH_JCR_CONTENT, TYPE_NT_RESOURCE, file);
            try (FileInputStream fis = new FileInputStream(file)) {
                Binary binary = session.getValueFactory().createBinary(fis);
                contentNode.setProperty("jcr:data", binary);
                contentNode.setProperty("jcr:mimeType", resolveMimeType(file.getName()));
                contentNode.setProperty("jcr:encoding", "UTF-8");
            } catch (IOException io) {
                throw new RepositoryException("Failed to import file: " + file, io);
            }
            session.save();
        }
    }

    public void adjustParentFolders(Session session, Node parentNode, File file) throws RepositoryException {
        // parent folders lastModified
        File parentFile = file.getParentFile();
        Node parent = parentNode;
        while (!parent.getName().equals(ROOT_FOLDER_NODE)) {
            applyMixins(parent, TYPE_NT_FOLDER, parentFile);
            session.save();
            parentFile = parentFile.getParentFile();
            parent = parent.getParent();
        }
    }

    private void applyMixins(Node node, String primaryType, File file) {
        jcrConfig.getMixinMap().getOrDefault(primaryType, List.of()).forEach(mixin -> {
            try {
                if (node.canAddMixin(mixin)) {
                    node.addMixin(mixin);
                }
                setMixinProps(file, node, mixin);
            } catch (Exception ex) {
                log.error(String.format("Error while set mixin = %s, file=%s", mixin, file.getAbsolutePath()), ex);
            }
        });
    }

    private void setMixinProps(File file, Node node, String mixinName) {
        List<String> pathParts = getPathParts(file.getPath());
        pathParts = pathParts.subList(pathParts.indexOf("webdav"), pathParts.size());
        PropSource propSource = buildPropSource(file, pathParts);

        Mixin mixin = mixinsProvider.getCurrentMixins().stream()
            .filter(m -> m.getName().equals(mixinName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(String.format("Mixin '%s' is not found in the system", mixinName)));

        mixin.getProperties().forEach(mixinProp -> {
            Function<Object, Value> valueFunc = mixinProp.getValueFunc();
            Function<PropSource, Object> propFunc = getPropSupplierMap().get(mixinProp);
            try {
                if (mixinProp == getlastmodified) {
                    long lastModified = getLastModified(file, SortBy.Latest);
                    node.setProperty(getlastmodified.getName(), new LongValue(lastModified));
                } else {
                    Object propResult = propFunc.apply(propSource);
                    if (propResult != null) {
                        Value value = valueFunc.apply(propResult);
                        node.setProperty(mixinProp.getName(), value);
                    }
                }
            } catch (Exception ex) {
                String msg = String.format(
                    "Error while setting property: file=%s prop=%s",
                    file.getAbsolutePath(), mixinProp.getName()
                );
                log.error(msg, ex);
            }
        });
    }
}