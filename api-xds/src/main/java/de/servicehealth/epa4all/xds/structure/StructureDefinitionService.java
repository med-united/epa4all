package de.servicehealth.epa4all.xds.structure;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.servicehealth.epa4all.xds.ebrim.DocumentDefinition;
import de.servicehealth.epa4all.xds.ebrim.FolderDefinition;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.xds.XDSUtils.isPdfCompliant;
import static de.servicehealth.epa4all.xds.XDSUtils.isXmlCompliant;

@ApplicationScoped
public class StructureDefinitionService {
	
	private static final Logger log = Logger.getLogger(StructureDefinitionService.class.getName());

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
    private StructureDefinition emptyStructureDefinition;

    private List<File> igSchemaFiles = new ArrayList<>();

    public void onStart(@Observes StartupEvent ev) {
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

        File schemaFolder = new File(schemasFolderPath);
        if (schemaFolder.exists()) {
            File[] files = schemaFolder.listFiles();
            if (files == null || files.length == 0) {
                throw new IllegalStateException("IG-Schema files not found");
            }
            igSchemaFiles = Arrays.asList(files);
        }
    }

    public StructureDefinition getStructureDefinition(String ig, String contentType, byte[] documentBytes) throws Exception {
        String schemaFileName = extractSchemaFileName(ig, contentType, documentBytes);
        Optional<File> igSchemaFile = igSchemaFiles.stream().filter(f -> f.getName().equalsIgnoreCase(schemaFileName)).findFirst();
        if (igSchemaFile.isPresent()) {
            log.info("%s IG schema was selected".formatted(igSchemaFile.get().getName()));
            return mapper.readValue(Files.readString(igSchemaFile.get().toPath()), StructureDefinition.class);
        } else {
            log.warning("Empty structure definition was selected");
            return emptyStructureDefinition;
        }
    }

    private String extractSchemaFileName(String ig, String contentType, byte[] documentBytes) throws Exception {
        if (ig != null && !ig.isEmpty()) {
            return "ig-" + ig + ".json";
        }
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
