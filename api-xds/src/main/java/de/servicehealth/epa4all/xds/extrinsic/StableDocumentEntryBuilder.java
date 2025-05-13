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

    @SuppressWarnings({"rawtypes", "unchecked"})
    public StableDocumentEntryBuilder finalize(
        String contentType,
        AuthorPerson authorPerson,
        String praxis,
        String practiceSetting,
        String information,
        String information2,
        List<FolderDefinition> folderDefinitions
    ) {
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
            .withLocalizedString(createLocalizedString(languageCode, praxis == null ? "Arztpraxis" : praxis))
            .build();
        classificationTypes.add(classificationType);

        classificationType = practiceSettingCodeClassificationBuilder
            .withClassifiedObject(documentId)
            .withNodeRepresentation("ALLG") // TODO check
            .withCodingScheme("1.3.6.1.4.1.19376.3.276.1.5.4")
            .withLocalizedString(createLocalizedString(languageCode, practiceSetting == null ? "Allgemeinmedizin" : practiceSetting))
            .build();
        classificationTypes.add(classificationType);


        folderDefinitions.stream()
            .filter(fd -> fd.getValue() instanceof List)
            .findFirst()
            .ifPresent(fd -> {
                if (fd.getName().equals("documentEntry.mimeType")) {
                    List<String> list = (List<String>) fd.getValue();
                    mimeType = list.stream().filter(m -> m.equals(contentType)).findFirst().orElse(list.getFirst());
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

                    if (contentType.contains("pdf")) {
                        if (name.contains("classCode") && information != null) {
                            text = information;
                        }
                        if (name.contains("typeCode") && information2 != null) {
                            text = information2;
                        }
                    }
                    final String textValue = text;

                    classificationBuilders.stream()
                        .filter(cb -> cb.getCodingSchemaType().equals("DE"))
                        .filter(cb -> cb.getName().equals(name))
                        .forEach(b -> classificationTypes.add(
                            b
                                .withMimeType(mimeType)
                                .withClassifiedObject(documentId)
                                .withNodeRepresentation(code)
                                .withCodingScheme(codeSystem)
                                .withLocalizedString(createLocalizedString(languageCode, textValue))
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
