package de.servicehealth.epa4all.server.jcr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PropertyType;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.PropertyDefinition;

public class NodesHelper {

    private static final Logger log = LoggerFactory.getLogger(NodesHelper.class.getName());
    
    public static void printNodeInfo(Session session, String type) {
        try {
            NodeTypeManager ntMgr = session.getWorkspace().getNodeTypeManager();

            NodeType nodeType = ntMgr.getNodeType(type);

            System.out.println();
            System.out.println("----------------------------------");
            System.out.println("Node type: " + nodeType.getName());
            System.out.println("Is mixin: " + nodeType.isMixin());
            System.out.println("Is abstract: " + nodeType.isAbstract());

            PropertyDefinition[] propDefs = nodeType.getDeclaredPropertyDefinitions();
            System.out.println("\nDeclared properties:");
            for (PropertyDefinition propDef : propDefs) {
                System.out.println(" - " + propDef.getName() +
                    " (type: " + PropertyType.nameFromValue(propDef.getRequiredType()) +
                    ", mandatory: " + propDef.isMandatory() +
                    ", multiple: " + propDef.isMultiple() + ")");
            }

            propDefs = nodeType.getPropertyDefinitions();
            System.out.println("\nAll properties (including inherited):");
            for (PropertyDefinition propDef : propDefs) {
                System.out.println(" - " + propDef.getName() +
                    " (from: " + propDef.getDeclaringNodeType().getName() +
                    ", type: " + PropertyType.nameFromValue(propDef.getRequiredType()) + ")");
            }

            NodeDefinition[] childDefs = nodeType.getChildNodeDefinitions();
            System.out.println("\nChild node definitions:");
            for (NodeDefinition childDef : childDefs) {
                System.out.println(" - " + childDef.getName() +
                    " (required types: " + String.join(", ", childDef.getRequiredPrimaryTypeNames()) + ")");
            }
        } catch (Exception e) {
            log.error("Error while printing node info", e);
        }
    }
}
