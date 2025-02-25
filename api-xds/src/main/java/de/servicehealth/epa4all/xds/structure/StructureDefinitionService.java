package de.servicehealth.epa4all.xds.structure;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.servicehealth.epa4all.xds.ebrim.DocumentDefinition;
import de.servicehealth.epa4all.xds.ebrim.FolderDefinition;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Setter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.xds.XDSUtils.isPdfCompliant;
import static de.servicehealth.epa4all.xds.XDSUtils.isXmlCompliant;

@ApplicationScoped
public class StructureDefinitionService {
	
	private static Logger log = Logger.getLogger(StructureDefinitionService.class.getName());

    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();

    static {
        DOCUMENT_BUILDER_FACTORY.setNamespaceAware(true);
    }

    @Setter
    @ConfigProperty(name = "ig.schema.folder.path")
    String schemasFolderPath;

    @ConfigProperty(name = "ig.schema.xml")
    Map<String, String> xmlSchemasMap;

    @ConfigProperty(name = "ig.schema.pdf")
    Map<String, String> pdfSchemasMap;

    private final ObjectMapper mapper = new ObjectMapper();
    private final StructureDefinition emptyStructureDefinition;

    public StructureDefinitionService() {
        emptyStructureDefinition = new StructureDefinition();

        FolderDefinition rootMetadata = new FolderDefinition();
        rootMetadata.setValue(Map.of(
            "code", "other"
        ));
        emptyStructureDefinition.setMetadata(rootMetadata);

        ArrayList<DocumentDefinition> elements = new ArrayList<>();
        DocumentDefinition documentDefinition = new DocumentDefinition();
        ArrayList<FolderDefinition> metadata = new ArrayList<>();
        documentDefinition.setMetadata(metadata);
        elements.add(documentDefinition);
        emptyStructureDefinition.setElements(elements);
    }

    public StructureDefinition getStructureDefinition(String contentType, byte[] documentBytes) throws Exception {
        String schemaFileName = extractSchemaFileName(contentType, documentBytes);
        if (schemaFileName == null) {
            return emptyStructureDefinition;
        }

        File schemaFolder = new File(schemasFolderPath);
        if (!schemaFolder.exists()) {
            log.warning("IG-Schema files are not found");
            return null;
        }
        File[] files = schemaFolder.listFiles(f ->
            f.exists() && f.getName().equalsIgnoreCase(schemaFileName)
        );
        if (files == null || files.length == 0) {
            log.warning(String.format("IG Schema file [%s] is not found", schemaFileName));
            return null;
        }
        return mapper.readValue(Files.readString(files[0].toPath()), StructureDefinition.class);
    }

    private String extractSchemaFileName(String contentType, byte[] documentBytes) throws Exception {
        if (isXmlCompliant(contentType)) {
            String xmlSource = new String(documentBytes);
            Optional<Map.Entry<String, String>> entryOpt = xmlSchemasMap.entrySet().stream()
                .filter(e -> xmlSource.contains(e.getKey()))
                .findFirst();
            if (entryOpt.isPresent()) {
                return entryOpt.get().getValue();
            }
            Document xmlDocument = toXmlDocument(xmlSource);
            NodeList documentTypeCd = xmlDocument.getElementsByTagName("document_type_cd");
            if (documentTypeCd.getLength() > 0) {
                Element element = (Element) documentTypeCd.item(0);
                return xmlSchemasMap.get(element.getAttribute("V"));
            } else {
                return null;
            }
        } else if(isPdfCompliant(contentType)) {
            return pdfSchemasMap.get("fallback");
        } else {
            return null;
        }
    }

    public static Document toXmlDocument(String xml) throws Exception {
        DocumentBuilder builder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }
}
