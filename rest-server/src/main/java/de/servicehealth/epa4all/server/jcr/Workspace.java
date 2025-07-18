package de.servicehealth.epa4all.server.jcr;

import de.servicehealth.epa4all.server.jcr.mixin.MixinContext;
import de.servicehealth.epa4all.server.propsource.PropBuilder;
import org.apache.jackrabbit.api.JackrabbitWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.RowIterator;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static de.servicehealth.epa4all.server.propsource.PropBuilder.SKIPPED_FILES;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.jcr.query.Query.JCR_SQL2;

public class Workspace {

    private static final Logger log = LoggerFactory.getLogger(Workspace.class.getName());

    public static final String ROOT_FOLDER_NODE = "rootFolder";

    private final PropBuilder propBuilder;
    private final JcrConfig jcrConfig;

    public Workspace(JcrConfig jcrConfig, PropBuilder propBuilder) {
        this.jcrConfig = jcrConfig;
        this.propBuilder = propBuilder;
    }

    private String getConfigurationParam(String configElement, File configFile, URL configUrl) {
        String path = configFile.exists() ? configFile.getAbsolutePath() : configUrl == null ? null : configUrl.getPath();
        return path == null ? "" : String.format(configElement, path);
    }

    private String getConfigElement(String name, String configName, String elementTemplate) {
        File configFile = new File(jcrConfig.getConfigPath(), configName);
        URL configUrl = Workspace.class.getResource("/jcr/" + configName);
        String configElement = getConfigurationParam(elementTemplate, configFile, configUrl);
        log.info(String.format("%s config = '%s'", name, configElement));
        return configElement;
    }

    private InputSource getInputSource(String telematikId) {
        String tikaConfigElement = getConfigElement(
            "Tika",
            "tika-config-enabled.xml",
            "\n<param name=\"tikaConfigPath\" value=\"%s\"/>"
        );
        String indexingConfigElement = getConfigElement(
            "Indexing",
            "indexing-config-enabled.xml",
            "\n<param name=\"indexingConfiguration\" value=\"%s\"/>"
        );

        String path = jcrConfig.getWorkspacesHome() + "/" + telematikId;
        String configXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Workspace name="%s">
                <FileSystem class="org.apache.jackrabbit.core.fs.local.LocalFileSystem">
                    <param name="path" value="%s"/>
                </FileSystem>
                <PersistenceManager class="org.apache.jackrabbit.core.persistence.bundle.BundleFsPersistenceManager"/>
                <SearchIndex class="org.apache.jackrabbit.core.query.lucene.SearchIndex">
                    <param name="path" value="%s/index"/>
                    <param name="useSimpleFSDirectory" value="true"/>
                    <param name="supportHighlighting" value="true"/>
                    <param name="mergeFactor" value="10"/>
                    <param name="maxMergeDocs" value="100000"/>
                    <param name="useCompoundFile" value="true"/>
                    <param name="extractorPoolSize" value="5"/>%s%s
                </SearchIndex>
            </Workspace>""".formatted(telematikId, path, path, tikaConfigElement, indexingConfigElement);

        return new InputSource(new ByteArrayInputStream(configXml.getBytes(UTF_8)));
    }

    public void deleteMixin(Session session, String staleMixinName) {
        String statement = String.format("SELECT * FROM [%s]", staleMixinName);
        String nodeName = null;
        try {
            Query query = session.getWorkspace().getQueryManager().createQuery(statement, JCR_SQL2);
            RowIterator rowIterator = query.execute().getRows();
            while (rowIterator.hasNext()) {
                Node node = rowIterator.nextRow().getNode();
                nodeName = node.getName();
                node.removeMixin(staleMixinName);
            }
        } catch (Exception e) {
            String msg = String.format(
                "Error while deleting staleMixinName mixin = %s from node = %s", staleMixinName, nodeName
            );
            log.error(msg, e);
        }
    }

    public boolean getOrCreate(Session defaultWsSession, String telematikId) throws RepositoryException {
        boolean created = false;
        List<String> accessibleWsNames = Arrays.asList(defaultWsSession.getWorkspace().getAccessibleWorkspaceNames());
        if (accessibleWsNames.contains(telematikId)) {
            log.info("Workspace '" + telematikId + "' already exists");
        } else {
            if (defaultWsSession.getWorkspace() instanceof JackrabbitWorkspace jackrabbitWorkspace) {
                InputSource inputSource = getInputSource(telematikId);
                jackrabbitWorkspace.createWorkspace(telematikId, inputSource);
                defaultWsSession.save();
                created = true;
                log.info("Workspace '" + telematikId + "' created successfully!");
            }
        }
        return created;
    }

    public void init(Session session, File telematikFolder, MixinContext mixinContext, boolean created) throws RepositoryException {
        if (created) {
            try {
                Node root = session.getRootNode();
                propBuilder.handleFolder(session, root, telematikFolder, ROOT_FOLDER_NODE, true);
            } catch (Exception e) {
                session.refresh(false);
                log.error("Error while creating folder node: " + telematikFolder.getName(), e);
            }
        } else {
            List<String> staleMixins = mixinContext.getStaleMixins();
            for (String unused : staleMixins) {
                deleteMixin(session, unused);
            }
        }
    }

    public void importFiles(File dir, Node parentNode, Session session, boolean staleMixins) throws RepositoryException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : Stream.of(files).filter(SKIPPED_FILES).toList()) {
            if (file.isDirectory()) {
                Node folderNode = propBuilder.handleFolder(session, parentNode, file, file.getName(), staleMixins);
                importFiles(file, folderNode, session, staleMixins);
            } else {
                propBuilder.handleFileCreation(session, parentNode, file, staleMixins);
            }
        }
    }
}