package de.servicehealth.epa4all.xds.structure;

import de.servicehealth.epa4all.xds.ebrim.FolderDefinition;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings({"rawtypes"})
public record ExtrinsicContext(
    String repositoryUniqueId,
    String uniqueId,
    String uri,
    String hash,
    String mimeType,
    ClassificationContext formatCode,
    ClassificationContext typeCode,
    ClassificationContext eventCodeList
) {

    public static final ExtrinsicContext defaultExtrinsicContext = new ExtrinsicContext(
        null, null, null, null, null, null, null, null
    );

    public boolean match(StructureDefinition structureDefinition) {
        return structureDefinition.getElements().stream()
            .anyMatch(dd -> {
                List<FolderDefinition> metadata = dd.getMetadata();

                Optional<FolderDefinition> formatCodeDefinition = findFolderDefinition(metadata, "documentEntry.formatCode");
                Optional<FolderDefinition> typeCodeDefinition = findFolderDefinition(metadata, "documentEntry.typeCode");
                Optional<FolderDefinition> eventCodeListDefinition = findFolderDefinition(metadata, "documentEntry.eventCodeList");
                Optional<FolderDefinition> mimeTypeDefinition = findFolderDefinition(metadata, "documentEntry.mimeType");

                return (contextMatchesDefinition(formatCode, formatCodeDefinition.orElse(null))
                    || contextMatchesDefinition(typeCode, typeCodeDefinition.orElse(null))
                    || contextMatchesDefinition(eventCodeList, eventCodeListDefinition.orElse(null)))
                    && mimeTypeMatchesDefinition(mimeType, mimeTypeDefinition.orElse(null));
            });
    }

    private static Optional<FolderDefinition> findFolderDefinition(List<FolderDefinition> metadata, String name) {
        return metadata.stream().filter(fd -> fd.getName().equals(name)).findFirst();
    }

    private static boolean contextMatchesDefinition(ClassificationContext context, FolderDefinition folderDefinition) {
        if (context == null || folderDefinition == null) {
            return false;
        }
        if (folderDefinition.getValue() instanceof Map map) {
            String code = (String) map.get("code");
            String codeSystem = (String) map.get("codeSystem");
            return context.codingSchema().equalsIgnoreCase(codeSystem)
                && context.nodeRepresentation().equalsIgnoreCase(code);
        } else {
            return false;
        }
    }

    private static boolean mimeTypeMatchesDefinition(String mimeType, FolderDefinition folderDefinition) {
        if (mimeType == null || folderDefinition == null) {
            return false;
        }
        return folderDefinition.getValue() instanceof List<?> list && list.contains(mimeType);
    }
}
