package de.servicehealth.epa4all.integration.base;

import de.servicehealth.epa4all.server.filetracker.FileEventSender;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.jcr.JcrConfig;
import de.servicehealth.epa4all.server.jcr.JcrRepositoryProvider;
import de.servicehealth.epa4all.server.jcr.JcrService;
import de.servicehealth.epa4all.server.jcr.TypesService;
import de.servicehealth.epa4all.server.propsource.PropBuilder;
import de.servicehealth.folder.WebdavConfig;
import io.quarkus.test.junit.QuarkusMock;
import jakarta.inject.Inject;

import javax.jcr.SimpleCredentials;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.servicehealth.epa4all.server.jcr.webdav.JCRParams.DEFAULT_AUTHENTICATE_HEADER;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("UnusedReturnValue")
public class AbstractWebdavIT {

    @Inject
    PropBuilder propBuilder;

    @Inject
    FileEventSender fileEventSender;

    @Inject
    TypesService typesService;

    @Inject
    JcrRepositoryProvider repositoryProvider;

    protected JcrConfig mockJcrConfig(File tempDir, Map<String, List<String>> mixinMap) {
        JcrConfig jcrConfig = mock(JcrConfig.class);
        File repository = new File(tempDir, "repository");
        repository.mkdirs();
        when(jcrConfig.getRepositoryHome()).thenReturn(repository);
        when(jcrConfig.getWorkspacesHome()).thenReturn(repository.getAbsolutePath() + "/workspaces");
        when(jcrConfig.getMissingAuthMapping()).thenReturn("admin:admin");
        when(jcrConfig.getResourcePathPrefix()).thenReturn("/webdav2");
        when(jcrConfig.getAuthenticateHeader()).thenReturn(DEFAULT_AUTHENTICATE_HEADER);
        when(jcrConfig.isCreateAbsoluteURI()).thenReturn(true);
        when(jcrConfig.getCredentials()).thenReturn(new SimpleCredentials("admin", "admin".toCharArray()));
        when(jcrConfig.getMixinMap()).thenReturn(mixinMap);
        when(jcrConfig.getReInitAttempts()).thenReturn(1);
        return jcrConfig;
    }

    protected WebdavConfig mockWebdavConfig(File tempDir, Map<String, List<String>> mixinMap) {
        Map<String, List<String>> map = Map.of(
            "nt:folder", List.of("epa:custom"),
            "nt:file", List.of("epa:custom")
        );
        JcrConfig jcrConfig = mockJcrConfig(tempDir, mixinMap == null ? map : mixinMap);
        WebdavConfig webdavConfig = mock(WebdavConfig.class);

        File webdav = new File(tempDir, "webdav");
        webdav.mkdirs();
        when(webdavConfig.getRootFolder()).thenReturn(webdav.getAbsolutePath());
        when(webdavConfig.getDefaultLimit()).thenReturn(20);
        when(webdavConfig.getAvailableProps(eq(true))).thenReturn(Map.of(
            "Mandatory", Arrays.asList("creationdate,getlastmodified,displayname,resourcetype".split(",")),
            "Root", Arrays.asList("".split("root")),
            "Telematik", Arrays.asList("smcb".split(",")),
            "Insurant", Arrays.asList("firstname,lastname,birthday".split(",")),
            "Category", Arrays.asList("firstname,lastname,birthday,entryuuid".split(","))
        ));
        when(webdavConfig.getAvailableProps(eq(false))).thenReturn(Map.of(
            "Mandatory", Arrays.asList("creationdate,getlastmodified,displayname".split(",")),
            "Checksum", Arrays.asList("entries".split(",")),
            "Entitlement", Arrays.asList("firstname,lastname,birthday,validto".split(",")),
            "Other", Arrays.asList("firstname,lastname,birthday,getcontenttype,getcontentlength".split(","))
        ));
        when(webdavConfig.getSmcbFolders()).thenReturn(
            Set.of(
                "eab_2ed345b1-35a3-49e1-a4af-d71ca4f23e57",
                "other_605a9f3c-bfe8-4830-a3e3-25a4ec6612cb",
                "local_00000000-0000-0000-0000-000000000000"
            )
        );

        FolderService folderService = new FolderService(webdavConfig, fileEventSender);
        JcrService jcrService = new JcrService(
            repositoryProvider,
            folderService,
            typesService,
            propBuilder,
            jcrConfig
        );

        QuarkusMock.installMockForType(jcrService, JcrService.class);
        QuarkusMock.installMockForType(folderService, FolderService.class);
        QuarkusMock.installMockForType(webdavConfig, WebdavConfig.class);
        QuarkusMock.installMockForType(jcrConfig, JcrConfig.class);
        return webdavConfig;
    }
}
