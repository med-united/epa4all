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

@ApplicationScoped
public class StructureDefinitionService {

    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();

    static {
        DOCUMENT_BUILDER_FACTORY.setNamespaceAware(true);
    }

    @Setter
    @ConfigProperty(name = "ig.schema.folder.path")
    String schemasFolderPath;

    private final ObjectMapper mapper = new ObjectMapper();
    private final StructureDefinition fallbackStructureDefinition;

    public StructureDefinitionService() {
        fallbackStructureDefinition = new StructureDefinition();
        ArrayList<DocumentDefinition> elements = new ArrayList<>();
        DocumentDefinition documentDefinition = new DocumentDefinition();
        ArrayList<FolderDefinition> metadata = new ArrayList<>();
        documentDefinition.setMetadata(metadata);
        elements.add(documentDefinition);
        fallbackStructureDefinition.setElements(elements);
    }

    private boolean xmlAwareContentType(String contentType) {
        return contentType.contains("xml"); // TODO extend
    }

    public StructureDefinition getStructureDefinition(String contentType, byte[] documentBytes) throws Exception {
        String documentType = extractDocumentType(contentType, documentBytes);
        if (documentType == null) {
            return fallbackStructureDefinition;
        }

        File schemaFolder = new File(schemasFolderPath);
        if (!schemaFolder.exists()) {
            throw new IllegalStateException("IG-Schema files are not found");
        }
        String schemaFileName = documentType + ".json";
        File[] files = schemaFolder.listFiles(f ->
            f.exists() && f.getName().equalsIgnoreCase(schemaFileName)
        );
        if (files == null || files.length == 0) {
            throw new IllegalStateException(String.format("IG Schema file [%s] is not found", schemaFileName));
        }
        return mapper.readValue(Files.readString(files[0].toPath()), StructureDefinition.class);
    }

    private String extractDocumentType(String contentType, byte[] documentBytes) throws Exception {
        if (xmlAwareContentType(contentType)) {
            Document xmlDocument = toXmlDocument(new String(documentBytes));
            NodeList documentTypeCd = xmlDocument.getElementsByTagName("document_type_cd");
            if (documentTypeCd.getLength() > 0) {
                Element element = (Element) documentTypeCd.item(0);
                return element.getAttribute("V");
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public static Document toXmlDocument(String xml) throws Exception {
        DocumentBuilder builder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }
}
