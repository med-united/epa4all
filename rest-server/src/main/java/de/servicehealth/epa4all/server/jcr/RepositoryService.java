package de.servicehealth.epa4all.server.jcr;

import de.servicehealth.epa4all.server.propsource.JcrProp;
import de.servicehealth.epa4all.server.propsource.PropBuilder;
import de.servicehealth.epa4all.server.propsource.PropSource;
import de.servicehealth.epa4all.server.rest.fileserver.paging.SortBy;
import de.servicehealth.folder.IFolderService;
import de.servicehealth.folder.WebdavConfig;
import de.servicehealth.startup.StartableService;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.jackrabbit.value.LongValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Binary;
import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Value;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.NodeTypeTemplate;
import javax.jcr.nodetype.PropertyDefinitionTemplate;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static de.servicehealth.epa4all.server.propsource.JcrProp.getlastmodified;
import static de.servicehealth.epa4all.server.propsource.PropBuilder.EPA_MIXIN_NAME;
import static de.servicehealth.epa4all.server.propsource.PropBuilder.EPA_NAMESPACE_PREFIX;
import static de.servicehealth.epa4all.server.propsource.PropBuilder.EPA_NAMESPACE_URI;
import static de.servicehealth.epa4all.server.rest.fileserver.prop.DirectoryProp.SKIPPED_FILES;
import static de.servicehealth.utils.MimeHelper.resolveMimeType;
import static de.servicehealth.utils.ServerUtils.getPathParts;

@Getter
@ApplicationScoped
public class RepositoryService extends StartableService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryService.class.getName());

    private final WorkspaceService workspaceService;
    private final IFolderService folderService;
    private final WebdavConfig webdavConfig;
    private final PropBuilder propBuilder;
    private final JcrConfig jcrConfig;

    private Repository repository;

    @Inject
    public RepositoryService(
        WorkspaceService workspaceService,
        IFolderService folderService,
        WebdavConfig webdavConfig,
        PropBuilder propBuilder,
        JcrConfig jcrConfig
    ) {
        this.workspaceService = workspaceService;
        this.folderService = folderService;
        this.webdavConfig = webdavConfig;
        this.propBuilder = propBuilder;
        this.jcrConfig = jcrConfig;
    }

    @Override
    public void onStart() throws Exception {
        try {
            File repositoryHome = jcrConfig.getRepositoryHome();
            if (!repositoryHome.exists()) {
                repositoryHome.mkdirs();
            }
            System.setProperty("workspaces.home", jcrConfig.getWorkspacesHome());
            System.setProperty("repository.home", repositoryHome.getAbsolutePath());

            InputStream is = RepositoryService.class.getResourceAsStream("/repository.xml");
            RepositoryConfig config = RepositoryConfig.create(is, repositoryHome.getAbsolutePath());
            this.repository = RepositoryImpl.create(config);

            SimpleCredentials credentials = new SimpleCredentials("admin", "admin".toCharArray());
            Session adminSession = repository.login(credentials);
            Arrays.stream(folderService.getTelematikFolders()).forEach(telematikFolder -> {
                String telematikId = telematikFolder.getName();
                try {
                    workspaceService.createWorkspace(adminSession, telematikId);
                    importFilesIntoWorkspace(credentials, telematikFolder, telematikId);
                } catch (Throwable e) {
                    log.error("Error while importing [" + telematikId + "] into JCR repository", e);
                }
            });
            adminSession.logout();
            
            log.info("Jackrabbit repository initialized in: " + repositoryHome);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Jackrabbit repository", e);
        }
    }

    public void importFilesIntoWorkspace(
        SimpleCredentials credentials,
        File telematikFolder,
        String workspaceName
    ) throws Exception {
        Session session = repository.login(credentials, workspaceName);
        try {
            Node rootNode = session.getRootNode();
            registerProperties(session);
            importDirectory(telematikFolder, rootNode, session);
            session.save();


            // Node targetNode = session.getNode("/3-SMC-B-Testkarte--883110000147807/jcr:root/X110683202");

            rootNode = session.getRootNode();
            Node node = rootNode.getNode("X110683202");
            System.out.println("Direct JCR access - has property: " + node.hasProperty("epa:firstname"));

        } catch (Exception e) {
            session.refresh(false);
            throw e;
        } finally {
            session.logout();
        }
    }

    private NodeType getMixinNodeType(NodeTypeManager typeManager) throws Exception {
        NodeTypeIterator mixinNodeTypes = typeManager.getMixinNodeTypes();
        while (mixinNodeTypes.hasNext()) {
            NodeType nodeType = mixinNodeTypes.nextNodeType();
            if (nodeType.getName().equals(EPA_MIXIN_NAME)) {
                return nodeType;
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerProperties(Session session) {
        try {
            Workspace workspace = session.getWorkspace();
            NamespaceRegistry namespaceRegistry = workspace.getNamespaceRegistry();
            if (!Arrays.asList(namespaceRegistry.getPrefixes()).contains(EPA_NAMESPACE_PREFIX)) {
                namespaceRegistry.registerNamespace(EPA_NAMESPACE_PREFIX, EPA_NAMESPACE_URI);
                session.save();
            }
            
            NodeTypeManager typeManager = session.getWorkspace().getNodeTypeManager();
            NodeType mixinNodeType = getMixinNodeType(typeManager);
            if (mixinNodeType == null) {
                NodeTypeTemplate typeTemplate = typeManager.createNodeTypeTemplate();
                typeTemplate.setName(EPA_MIXIN_NAME);
                typeTemplate.setMixin(true);

                List templates = typeTemplate.getPropertyDefinitionTemplates();
                Set<String> existing = new HashSet(templates.stream()
                    .map(obj -> ((PropertyDefinitionTemplate) obj).getName())
                    .toList()
                );
                propBuilder.getPropSupplierMap().keySet().forEach(p -> {
                    if (!existing.contains(p.epaName())) {
                        try {
                            PropertyDefinitionTemplate template = typeManager.createPropertyDefinitionTemplate();
                            template.setName(p.epaName());
                            template.setRequiredType(p.getType());
                            template.setQueryOrderable(p == getlastmodified);
                            template.setFullTextSearchable(p.isSearchable());
                            templates.add(template);
                        } catch (Exception e) {
                            log.error("Error while creating property type: " + p, e);
                        }
                    }
                });
                typeManager.registerNodeType(typeTemplate, true);
            }
        } catch (Exception e) {
            log.error("Error while registering JCR property", e);
        }
    }

    private void setEpaProps(File file, Node node) {
        boolean directory = file.isDirectory();

        List<String> pathParts = getPathParts(file.getPath());
        pathParts = pathParts.subList(pathParts.indexOf("webdav"), pathParts.size());
        PropSource propSource = propBuilder.buildPropSource(file, pathParts);

        int level = pathParts.size() - 1;
        List<String> props = directory
            ? propBuilder.resolveDirectoryProps(level).stream().toList()
            : propBuilder.resolveFileProps(file);

        propBuilder.getPropSupplierMap().entrySet().stream()
            .filter(e -> props.contains(e.getKey().name()))
            .forEach(e -> {
                JcrProp jcrProp = e.getKey();
                Function<PropSource, Object> propFunc = e.getValue();
                Function<Object, Value> valueFunc = jcrProp.getValueFunc();
                try {
                    if (jcrProp == getlastmodified) {
                        long lastModified = propBuilder.getLastModified(file, SortBy.Latest);
                        node.setProperty(getlastmodified.epaName(), new LongValue(lastModified));
                    } else {
                        Value value = valueFunc.apply(propFunc.apply(propSource));
                        node.setProperty(jcrProp.epaName(), value);
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

    private void importDirectory(File dir, Node parentNode, Session session) throws RepositoryException {
        List<File> files = getFiles(dir);
        for (File file : files) {
            if (file.isDirectory()) {
                Node dirNode;
                try {
                    dirNode = parentNode.getNode(file.getName());
                } catch (PathNotFoundException e) {
                    dirNode = parentNode.addNode(file.getName(), "nt:folder");
                    if (!dirNode.isNodeType(EPA_MIXIN_NAME)) {
                        dirNode.addMixin(EPA_MIXIN_NAME);
                    }
                    try {
                        setEpaProps(file, dirNode);
                    } catch (Exception ex) {
                        log.error("Error while setEpaProps", ex);
                    }
                }
                importDirectory(file, dirNode, session);
            } else {
                try {
                    parentNode.getNode(file.getName());

                    //todo update lastModified ?
                    
                } catch (PathNotFoundException e) {
                    Node fileNode = parentNode.addNode(file.getName(), "nt:file");
                    Node contentNode = fileNode.addNode("jcr:content", "nt:resource");
                    if (!contentNode.isNodeType(EPA_MIXIN_NAME)) {
                        contentNode.addMixin(EPA_MIXIN_NAME);
                    }
                    try (FileInputStream fis = new FileInputStream(file)) {
                        Binary binary = session.getValueFactory().createBinary(fis);
                        contentNode.setProperty("jcr:data", binary);
                        contentNode.setProperty("jcr:mimeType", resolveMimeType(file.getName()));
                        setEpaProps(file, contentNode);
                    } catch (Exception io) {
                        throw new RepositoryException("Failed to import file: " + file, io);
                    }
                }
            }
        }
    }

    private List<File> getFiles(File dir) {
        File[] files = dir.listFiles();
        return files == null
            ? List.of()
            : Stream.of(files).filter(f -> SKIPPED_FILES.stream().noneMatch(s -> s.equals(f.getName()))).toList();
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (repository instanceof RepositoryImpl) {
                log.info("Shutting down Jackrabbit repository...");
                ((RepositoryImpl) repository).shutdown();
                log.info("Jackrabbit repository shutdown complete");
            }
        } catch (Exception e) {
            log.error("Error shutting down repository", e);
        }
    }
}
