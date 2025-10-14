package de.servicehealth.epa4all.xds.extrinsic;

import de.servicehealth.epa4all.xds.CustomCodingScheme;
import de.servicehealth.epa4all.xds.author.AuthorPerson;
import de.servicehealth.epa4all.xds.classification.ClassificationBuilder;
import de.servicehealth.epa4all.xds.classification.de.DocumentEntry;
import de.servicehealth.epa4all.xds.classification.de.ExtrinsicAuthorClassificationBuilder;
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
import java.util.Optional;
import java.util.Set;

import static de.servicehealth.epa4all.xds.CodingScheme.FacilityTypeCodeClassification;
import static de.servicehealth.epa4all.xds.CodingScheme.PracticeSettingClassification;
import static de.servicehealth.epa4all.xds.XDSUtils.createLocalizedString;

@Dependent
public class StableDocumentEntryBuilder extends ExtrinsicObjectTypeBuilder<StableDocumentEntryBuilder> {

    public static final String DE_STABLE_OBJECT_TYPE = "urn:uuid:7edca82f-054d-47f2-a032-9b2a5b5186c1";

    @Inject
    @DocumentEntry
    Instance<ClassificationBuilder<?>> classificationBuilders;

    @Inject
    @DocumentEntry
    ExtrinsicAuthorClassificationBuilder extrinsicAuthorClassificationBuilder;

    @Inject
    @DocumentEntry
    FacilityTypeCodeClassificationBuilder facilityClassificationBuilder;

    @Inject
    @DocumentEntry
    PracticeSettingCodeClassificationBuilder practiceSettingCodeClassificationBuilder;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public StableDocumentEntryBuilder finalize(
        String contentType,
        AuthorPerson authorPerson,
        String telematikId,
        String authorInstitution,
        String praxis,
        String practiceSetting,
        String information,
        String information2,
        List<CustomCodingScheme> customCodingSchemes,
        List<FolderDefinition> folderDefinitions
    ) {
        List<ClassificationType> classificationTypes = new ArrayList<>();

        ClassificationType classificationType = extrinsicAuthorClassificationBuilder
            .withClassifiedObject(documentId)
            .withTelematikId(telematikId)
            .withAuthorPerson(authorPerson)
            .withAuthorInstitution(authorInstitution)
            .build();
        classificationTypes.add(classificationType);

        classificationTypes.add(prepareFacilitySetting(customCodingSchemes, praxis));
        classificationTypes.add(preparePracticeSetting(customCodingSchemes, practiceSetting));

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
                    String codeSystem = (String) map.get("codeSystem");

                    Optional<CustomCodingScheme> customCodingScheme = customCodingSchemes.stream()
                        .filter(cs -> cs.getCodingScheme().getCode().equals(codeSystem))
                        .findFirst();

                    String code = (String) map.get("code");
                    String nodeRepresentation = customCodingScheme.isPresent()
                        ? customCodingScheme.get().getNodeRepresentationOrDefault(code)
                        : code;
                    
                    List descList = (List) map.get("desc");
                    String text;
                    if (descList == null || descList.isEmpty()) {
                        text = (String) map.get("displayName");
                    } else {
                        Map descMap = (Map) descList.getFirst();
                        text = (String) descMap.get("#text");
                    }

                    if (customCodingScheme.isPresent()) {
                        text = customCodingScheme.get().getNameOrDefault(text);
                    } else {
                        if (name.contains("classCode") && information != null) {
                            text = information;
                        }
                        if (name.contains("typeCode") && information2 != null) {
                            text = information2;
                        }
                    }
                    final String textValue = text;

                    classificationBuilders.stream()
                        .filter(cb -> cb.getName().equals(name))
                        .forEach(b -> classificationTypes.add(
                            b
                                .withMimeType(mimeType)
                                .withClassifiedObject(documentId)
                                .withNodeRepresentation(nodeRepresentation)
                                .withCodingScheme(codeSystem)
                                .withLocalizedString(createLocalizedString(languageCode, textValue))
                                .build()
                        ));
                }
            });

        return withClassifications(classificationTypes.toArray(ClassificationType[]::new));
    }

    private ClassificationType preparePracticeSetting(
        List<CustomCodingScheme> customCodingSchemes,
        String practiceSetting
    ) {
        Optional<CustomCodingScheme> practiceSettingScheme = customCodingSchemes.stream()
            .filter(cs -> cs.getCodingScheme().equals(PracticeSettingClassification))
            .findFirst();

        ClassificationType classificationType;
        if (practiceSettingScheme.isPresent()) {
            String nodeRepresentation = practiceSettingScheme.get().getNodeRepresentationOrDefault("ALLG");
            String name = practiceSettingScheme.get().getNameOrDefault("Allgemeinmedizin");
            classificationType = practiceSettingCodeClassificationBuilder
                .withClassifiedObject(documentId)
                .withNodeRepresentation(nodeRepresentation)
                .withCodingScheme(practiceSettingScheme.get().getCodingScheme().getCode())
                .withLocalizedString(createLocalizedString(languageCode, name))
                .build();
        } else {
            String name = practiceSetting == null ? "Allgemeinmedizin" : practiceSetting;
            classificationType = practiceSettingCodeClassificationBuilder
                .withClassifiedObject(documentId)
                .withNodeRepresentation("ALLG")
                .withCodingScheme("1.3.6.1.4.1.19376.3.276.1.5.4")
                .withLocalizedString(createLocalizedString(languageCode, name))
                .build();
        }
        return classificationType;
    }

    private ClassificationType prepareFacilitySetting(
        List<CustomCodingScheme> customCodingSchemes,
        String praxis
    ) {
        Optional<CustomCodingScheme> facilityScheme = customCodingSchemes.stream()
            .filter(cs -> cs.getCodingScheme().equals(FacilityTypeCodeClassification))
            .findFirst();

        ClassificationType classificationType;
        if (facilityScheme.isPresent()) {
            String nodeRepresentation = facilityScheme.get().getNodeRepresentationOrDefault("PRA");
            String name = facilityScheme.get().getNameOrDefault("Arztpraxis");
            classificationType = facilityClassificationBuilder
                .withClassifiedObject(documentId)
                .withNodeRepresentation(nodeRepresentation)
                .withCodingScheme(facilityScheme.get().getCodingScheme().getCode())
                .withLocalizedString(createLocalizedString(languageCode, name))
                .build();
        } else {
            String name = praxis == null ? "Arztpraxis" : praxis;
            classificationType = facilityClassificationBuilder
                .withClassifiedObject(documentId)
                .withNodeRepresentation("PRA")
                .withCodingScheme("1.3.6.1.4.1.19376.3.276.1.5.2")
                .withLocalizedString(createLocalizedString(languageCode, name))
                .build();
        }
        return classificationType;
    }

    @Override
    public ExtrinsicObjectType build() {
        ExtrinsicObjectType extrinsicObjectType = super.build();
        extrinsicObjectType.setObjectType(DE_STABLE_OBJECT_TYPE);
        return extrinsicObjectType;
    }
}
