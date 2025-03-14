package de.servicehealth.epa4all.server.jcr;

import de.servicehealth.epa4all.server.propsource.PropBuilder;
import de.servicehealth.folder.WebdavConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NamespaceRegistry;
import javax.jcr.PropertyType;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.NodeTypeTemplate;
import javax.jcr.nodetype.PropertyDefinitionTemplate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.EPA_FLEX_FOLDER;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.EPA_MIXIN_NAME;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.getlastmodified;

@SuppressWarnings({"unchecked", "rawtypes"})
@ApplicationScoped
public class TypesService {

    private static final Logger log = LoggerFactory.getLogger(TypesService.class.getName());

    private final WebdavConfig webdavConfig;
    private final PropBuilder propBuilder;

    @Inject
    public TypesService(WebdavConfig webdavConfig, PropBuilder propBuilder) {
        this.webdavConfig = webdavConfig;
        this.propBuilder = propBuilder;
    }

    public void registerFlexFolderType(Session session) {
        try {
            NodeTypeManager ntMgr = session.getWorkspace().getNodeTypeManager();

            NodeTypeTemplate template = ntMgr.createNodeTypeTemplate();
            template.setName(EPA_FLEX_FOLDER);

            template.setDeclaredSuperTypeNames(new String[]{"nt:folder"});

            PropertyDefinitionTemplate propDef = ntMgr.createPropertyDefinitionTemplate();
            propDef.setName("*");
            propDef.setRequiredType(PropertyType.UNDEFINED);
            propDef.setMultiple(false);
            propDef.setMandatory(false);
            propDef.setAutoCreated(false);

            template.getPropertyDefinitionTemplates().add(propDef);
            ntMgr.registerNodeType(template, true);
        } catch (Exception e) {
            log.error("Error while registerFlexFolderType", e);
        }
    }

    public void registerNamespace(Session session, String nsPrefix, String nsUri) {
        try {
            Workspace workspace = session.getWorkspace();
            NamespaceRegistry namespaceRegistry = workspace.getNamespaceRegistry();

            if (!Arrays.asList(namespaceRegistry.getPrefixes()).contains(nsPrefix)) {
                namespaceRegistry.registerNamespace(nsPrefix, nsUri);
                session.save();
            }
        } catch (Exception e) {
            log.error(String.format("Error while registering namespace, prefix=%s, uri=%s", nsPrefix, nsUri), e);
        }
    }

    public void registerEpaMixin(Session session) {
        try {
            // printNodeInfo(session, "nt:folder");

            NodeTypeManager typeManager = session.getWorkspace().getNodeTypeManager();
            NodeType mixinNodeType = getMixinNodeType(typeManager);
            if (mixinNodeType != null) {
                return;
            }
            NodeTypeTemplate typeTemplate = typeManager.createNodeTypeTemplate();
            typeTemplate.setName(EPA_MIXIN_NAME);
            typeTemplate.setMixin(true);
            typeTemplate.setQueryable(true);
            typeTemplate.setOrderableChildNodes(false);

            List templates = typeTemplate.getPropertyDefinitionTemplates();
            Set<String> existing = new HashSet(templates.stream()
                .map(obj -> ((PropertyDefinitionTemplate) obj).getName())
                .toList()
            );
            List<String> mandatory = webdavConfig.getAvailableProps(false).get("Mandatory");
            propBuilder.getPropSupplierMap().keySet().forEach(p -> {
                if (!existing.contains(p.epaName())) {
                    try {
                        PropertyDefinitionTemplate template = typeManager.createPropertyDefinitionTemplate();
                        template.setName(p.epaName());
                        template.setMandatory(mandatory.contains(p.name()));
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
            session.save();
        } catch (Exception e) {
            log.error("Error while registering JCR property", e);
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
}