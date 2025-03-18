package de.servicehealth.epa4all.server.jcr;

import de.servicehealth.epa4all.server.jcr.mixin.Mixin;
import de.servicehealth.epa4all.server.jcr.mixin.MixinContext;
import de.servicehealth.epa4all.server.jcr.mixin.MixinsProvider;
import de.servicehealth.epa4all.server.jcr.prop.MixinProp;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NamespaceRegistry;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.NodeTypeTemplate;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.nodetype.PropertyDefinitionTemplate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.EPA_FLEX_FOLDER;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.entryuuid;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.smcb;
import static de.servicehealth.epa4all.server.jcr.prop.MixinProp.EPA_NAMESPACE_PREFIX;
import static java.util.stream.Collectors.toMap;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
@ApplicationScoped
public class TypesService {

    private static final Logger log = LoggerFactory.getLogger(TypesService.class.getName());

    private final MixinsProvider mixinsProvider;

    @Inject
    public TypesService(MixinsProvider mixinsProvider) {
        this.mixinsProvider = mixinsProvider;
    }

    public void registerFlexFolderType(Session session) throws RepositoryException {
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
    }

    public void registerNamespace(Session session, String nsPrefix, String nsUri) throws RepositoryException {
        Workspace workspace = session.getWorkspace();
        NamespaceRegistry namespaceRegistry = workspace.getNamespaceRegistry();

        if (!Arrays.asList(namespaceRegistry.getPrefixes()).contains(nsPrefix)) {
            namespaceRegistry.registerNamespace(nsPrefix, nsUri);
            session.save();
        }
    }

    public MixinContext trackEpaMixins(Session session) throws RepositoryException {
        List<String> staleMixins = new ArrayList<>();
        List<NodeType> existingMixins = new ArrayList<>();

        Map<String, Mixin> currentMixins = mixinsProvider.getCurrentMixins().stream().collect(toMap(Mixin::getName, mixin -> mixin));

        NodeTypeManager typeManager = session.getWorkspace().getNodeTypeManager();
        NodeTypeIterator mixinNodeTypes = typeManager.getMixinNodeTypes();
        while (mixinNodeTypes.hasNext()) {
            NodeType nodeType = mixinNodeTypes.nextNodeType();
            if (nodeType.getName().startsWith(EPA_NAMESPACE_PREFIX)) {
                if (currentMixins.containsKey(nodeType.getName())) {
                    existingMixins.add(nodeType);
                } else {
                    staleMixins.add(nodeType.getName());
                }
            }
        }
        // create new
        List<Mixin> newMixins = mixinsProvider.getCurrentMixins().stream()
            .filter(m -> existingMixins.stream().noneMatch(em -> em.getName().equals(m.getName())))
            .toList();

        for (Mixin newMixin: newMixins) {
            addMixin(session, typeManager, newMixin);
        }

        // modify existing
        existingMixins.forEach(existing -> {
            Mixin mixin = currentMixins.get(existing.getName());
            if (mixin != null) {
                Set<MixinProp> currentMixinProps = new HashSet<>(mixin.getProperties());
                PropertyDefinition[] definitions = existing.getDeclaredPropertyDefinitions();
                boolean modified = definitions.length != currentMixinProps.size();
                if (!modified) {
                    for (PropertyDefinition pd : definitions) {
                        MixinProp prop = currentMixinProps.stream()
                            .filter(p -> p.getName().equals(pd.getName()))
                            .findFirst()
                            .orElse(null);
                        if (prop == null) {
                            modified = true;
                            break;
                        }
                        if (!prop.equalTo(pd)) {
                            modified = true;
                            break;
                        }
                    }
                }
                if (modified) {
                    staleMixins.add(existing.getName());
                }
            }
        });
        return new MixinContext(staleMixins);
    }

    private void addMixin(Session session, NodeTypeManager typeManager, Mixin mixin) throws RepositoryException {
        NodeTypeTemplate typeTemplate = typeManager.createNodeTypeTemplate();
        typeTemplate.setName(mixin.getName());
        typeTemplate.setMixin(true);
        typeTemplate.setQueryable(true);

        List templates = typeTemplate.getPropertyDefinitionTemplates();
        mixin.getProperties().forEach(p -> {
            try {
                PropertyDefinitionTemplate template = typeManager.createPropertyDefinitionTemplate();
                template.setName(p.getName());
                template.setMandatory(p.isMandatory());
                template.setRequiredType(p.getType());
                template.setQueryOrderable(p != entryuuid && p != smcb);
                template.setFullTextSearchable(p.isFulltext());
                templates.add(template);
            } catch (Exception e) {
                log.error("Error while creating property type: " + p, e);
            }
        });
        typeManager.registerNodeType(typeTemplate, true);
        session.save();
    }
}