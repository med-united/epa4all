package de.servicehealth.epa4all.xds.extrinsic;

import de.servicehealth.epa4all.xds.author.AuthorPerson;
import de.servicehealth.epa4all.xds.classification.ClassificationBuilder;
import de.servicehealth.epa4all.xds.classification.de.AuthorClassificationBuilder;
import de.servicehealth.epa4all.xds.classification.de.FacilityTypeCodeClassificationBuilder;
import de.servicehealth.epa4all.xds.classification.de.PracticeSettingCodeClassificationBuilder;
import de.servicehealth.epa4all.xds.ebrim.FolderDefinition;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.servicehealth.epa4all.xds.XDSUtils.createLocalizedString;

@Dependent
public class StableDocumentEntryBuilder extends ExtrinsicObjectTypeBuilder<StableDocumentEntryBuilder> {

    public static final String DE_STABLE_OBJECT_TYPE = "urn:uuid:7edca82f-054d-47f2-a032-9b2a5b5186c1";

    @Inject
    Instance<ClassificationBuilder<?>> classificationBuilders;

    @Inject
    AuthorClassificationBuilder authorClassificationBuilder;

    @Inject
    FacilityTypeCodeClassificationBuilder facilityClassificationBuilder;

    @Inject
    PracticeSettingCodeClassificationBuilder practiceSettingCodeClassificationBuilder;

    @SuppressWarnings({"rawtypes"})
    public StableDocumentEntryBuilder finalize(AuthorPerson authorPerson, List<FolderDefinition> folderDefinitions) {
        List<ClassificationType> classificationTypes = new ArrayList<>();

        ClassificationType classificationType = authorClassificationBuilder
            .withAuthorPerson(authorPerson)
            .withClassifiedObject(documentId)
            .withNodeRepresentation(authorPerson.getNodeRepresentation())
            .build();
        classificationTypes.add(classificationType);

        classificationType = facilityClassificationBuilder
            .withClassifiedObject(documentId)
            .withNodeRepresentation(authorPerson.getNodeRepresentation())
            .withCodingScheme("1.3.6.1.4.1.19376.3.276.1.5.2")
            .withLocalizedString(createLocalizedString(languageCode, "Arztpraxis")) // TODO
            .build();
        classificationTypes.add(classificationType);

        classificationType = practiceSettingCodeClassificationBuilder
            .withClassifiedObject(documentId)
            .withNodeRepresentation("ALLG") // TODO check
            .withCodingScheme("1.3.6.1.4.1.19376.3.276.1.5.4")
            .withLocalizedString(createLocalizedString(languageCode, "Allgemeinmedizin")) // TODO
            .build();
        classificationTypes.add(classificationType);


        folderDefinitions.stream()
            .filter(fd -> fd.getValue() instanceof List)
            .findFirst()
            .ifPresent(fd -> {
                if (fd.getName().equals("documentEntry.mimeType")) {
                    List list = (List) fd.getValue();
                    if (!list.isEmpty()) {
                        mimeType = (String) list.getFirst();
                    }
                }
            });

        Set<String> mandatoryClassificationTypes = Set.of(
            "authorPerson", "healthcareFacilityTypeCode", "practiceSettingCode"
        );

        folderDefinitions.stream()
            .filter(fd -> mandatoryClassificationTypes.stream().noneMatch(fd.getName()::contains))
            .forEach(fd -> {
                String name = fd.getName();
                Object obj = fd.getValue();
                if (obj instanceof Map map) {
                    String codeSystem;
                    if (name.contains("formatCode")) {
                        codeSystem = "1.3.6.1.4.1.19376.1.2.3"; // TODO check
                    } else {
                        codeSystem = (String) map.get("codeSystem");
                    }
                    String code = (String) map.get("code");
                    List descList = (List) map.get("desc");
                    String text;
                    if (descList == null || descList.isEmpty()) {
                        text = (String) map.get("displayName");
                    } else {
                        Map descMap = (Map) descList.getFirst();
                        text = (String) descMap.get("#text");
                    }
                    classificationBuilders.stream()
                        .filter(cb -> cb.getCodingSchemaType().equals("DE"))
                        .filter(cb -> cb.getName().equals(name))
                        .forEach(b -> classificationTypes.add(
                            b
                                .withMimeType(mimeType)
                                .withClassifiedObject(documentId)
                                .withNodeRepresentation(code)
                                .withCodingScheme(codeSystem)
                                .withLocalizedString(createLocalizedString(languageCode, text))
                                .build()
                        ));
                }
            });

        return withClassifications(classificationTypes.toArray(ClassificationType[]::new));
    }

    @Override
    public ExtrinsicObjectType build() {
        ExtrinsicObjectType extrinsicObjectType = super.build();
        extrinsicObjectType.setObjectType(DE_STABLE_OBJECT_TYPE);
        return extrinsicObjectType;
    }
}
