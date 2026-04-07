package de.servicehealth.epa4all.xds.structure;

import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExternalIdentifierType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;

import java.util.Optional;

import static de.servicehealth.epa4all.xds.XDSUtils.isPdfCompliant;
import static de.servicehealth.epa4all.xds.XDSUtils.isXmlCompliant;
import static de.servicehealth.epa4all.xds.classification.de.EventCodeListClassificationBuilder.EVENT_CODE_LIST_CLASSIFICATION_SCHEME;
import static de.servicehealth.epa4all.xds.classification.de.FormatCodeClassificationBuilder.FORMAT_CODE_CLASSIFICATION_SCHEME;
import static de.servicehealth.epa4all.xds.classification.de.TypeCodeClassificationBuilder.TYPE_CODE_CLASSIFICATION_SCHEME;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

@SuppressWarnings({"SameParameterValue"})
public class ExtrinsicHelper {

    public static ExtrinsicObjectType getExtrinsicObject(ProvideAndRegisterDocumentSetRequestType documentSetRequest) {
        RegistryObjectListType registryObjectList = documentSetRequest.getSubmitObjectsRequest().getRegistryObjectList();
        return registryObjectList.getIdentifiable()
            .stream()
            .filter(id -> id.getValue() instanceof ExtrinsicObjectType)
            .findFirst()
            .map(id -> (ExtrinsicObjectType) id.getValue())
            .orElseThrow(() -> new XdsException("", BAD_REQUEST));
    }

    public static ExtrinsicObjectType getExtrinsicObject(AdhocQueryResponse adhocQueryResponse, String uniqueId) {
        return adhocQueryResponse.getRegistryObjectList()
            .getIdentifiable()
            .stream()
            .map(id -> (ExtrinsicObjectType) id.getValue())
            .filter(extrinsicObject -> findExternalIdentifier(extrinsicObject, "XDSDocumentEntry.uniqueId")
                .map(externalIdentifier -> externalIdentifier.getValue().contains(uniqueId))
                .isPresent()
            )
            .findFirst()
            .orElseThrow(() -> new XdsException("ExtrinsicObject not found for '%s'".formatted(uniqueId), NOT_FOUND));
    }

    public static ExtrinsicContext buildExtrinsicContext(ExtrinsicObjectType extrinsicObject) {
        String mimeType = extrinsicObject.getMimeType();

        String uri = getSlotValue(extrinsicObject, "URI", true);
        String hash = getSlotValue(extrinsicObject, "hash", false);
        String repositoryUniqueId = getSlotValue(extrinsicObject, "repositoryUniqueId", false);
        String uniqueId = getExternalIdentifierValue(extrinsicObject, "XDSDocumentEntry.uniqueId");

        ClassificationContext formatCode = getClassificationContext(extrinsicObject, FORMAT_CODE_CLASSIFICATION_SCHEME);
        ClassificationContext typeCode = getClassificationContext(extrinsicObject, TYPE_CODE_CLASSIFICATION_SCHEME);
        ClassificationContext eventCodeList = getClassificationContext(extrinsicObject, EVENT_CODE_LIST_CLASSIFICATION_SCHEME);

        return new ExtrinsicContext(
            repositoryUniqueId,
            uniqueId,
            uri,
            hash,
            mimeType,
            formatCode,
            typeCode,
            eventCodeList
        );
    }

    private static String getSlotValue(ExtrinsicObjectType extrinsicObject, String slotName, boolean force) {
        Optional<String> opt = extrinsicObject.getSlot()
            .stream()
            .filter(s -> s.getName().equalsIgnoreCase(slotName))
            .findFirst().map(st -> st.getValueList().getValue().getFirst());
        if (force) {
            return opt.orElseThrow(() -> new XdsException("%s not found".formatted(slotName), BAD_REQUEST));
        } else {
            return opt.orElse(null);
        }
    }

    private static Optional<ExternalIdentifierType> findExternalIdentifier(
        ExtrinsicObjectType extrinsicObject,
        String identifierName
    ) {
        return extrinsicObject
            .getExternalIdentifier().stream()
            .filter(e -> e.getName().getLocalizedString().getFirst().getValue().equalsIgnoreCase(identifierName))
            .findFirst();
    }

    private static String getExternalIdentifierValue(ExtrinsicObjectType extrinsicObject, String identifierName) {
        return findExternalIdentifier(extrinsicObject, identifierName)
            .map(ExternalIdentifierType::getValue)
            .orElseThrow(() -> new XdsException("%s not found".formatted(identifierName), BAD_REQUEST));
    }

    private static ClassificationContext getClassificationContext(
        ExtrinsicObjectType extrinsicObject,
        String classificationScheme
    ) {
        return extrinsicObject.getClassification().stream()
            .filter(c -> classificationScheme.equalsIgnoreCase(c.getClassificationScheme()))
            .findFirst()
            .map(c -> new ClassificationContext(
                c.getNodeRepresentation(),
                c.getSlot().getFirst().getValueList().getValue().getFirst()
            )).orElse(null);
    }

    public static String getFallbackFileName(String uniqueId, String mimeType) {
        return switch (mimeType) {
            case String m when (isXmlCompliant(m) && !uniqueId.endsWith(".xml")) -> uniqueId + ".xml";
            case String m when (isPdfCompliant(m) && !uniqueId.endsWith(".pdf")) -> uniqueId + ".pdf";
            default -> uniqueId;
        };
    }
}
