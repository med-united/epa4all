package de.servicehealth.epa4all.xds.extrinsic;

import de.servicehealth.epa4all.xds.author.AuthorPerson;
import de.servicehealth.epa4all.xds.classification.ClassificationBuilder;
import de.servicehealth.epa4all.xds.classification.de.AuthorClassificationBuilder;
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

import static de.servicehealth.epa4all.xds.XDSUtils.createLocalizedString;

@Dependent
public class StableDocumentEntryBuilder extends ExtrinsicObjectTypeBuilder<StableDocumentEntryBuilder> {

    public static final String DE_STABLE_OBJECT_TYPE = "urn:uuid:7edca82f-054d-47f2-a032-9b2a5b5186c1";

    @Inject
    Instance<ClassificationBuilder<?>> classificationBuilders;

    @SuppressWarnings("unchecked")
    public StableDocumentEntryBuilder finalize(AuthorPerson authorPerson, List<FolderDefinition> folderDefinitions) {
        List<ClassificationType> classificationTypes = new ArrayList<>();

        Optional<ClassificationBuilder<?>> authorClassificationBuilderOpt = classificationBuilders
            .stream()
            .filter(cb -> cb instanceof AuthorClassificationBuilder)
            .findFirst();
        if (authorClassificationBuilderOpt.isPresent()) {
            ClassificationBuilder<?> classificationBuilder = authorClassificationBuilderOpt.get();
            AuthorClassificationBuilder authorClassificationBuilder = (AuthorClassificationBuilder) classificationBuilder;
            ClassificationType classificationType = authorClassificationBuilder
                .withAuthorPerson(authorPerson)
                .withClassifiedObject(documentId)
                .withNodeRepresentation(authorPerson.getNodeRepresentation())
                .build();
            classificationTypes.add(classificationType);
        }

        folderDefinitions.forEach(fd -> {
            Map<String, Object> map = (Map<String, Object>) fd.getValue();
            classificationBuilders.stream()
                .filter(cb -> cb.getCodingSchemaType().equals("DE"))
                .filter(cb -> cb.getCodingSchema().equals(map.get("codeSystem")))
                .forEach(b -> classificationTypes.add(
                    b
                        .withMimeType(mimeType)
                        .withClassifiedObject(documentId)
                        .withNodeRepresentation((String) map.get("code"))
                        .withLocalizedString(createLocalizedString(languageCode, (String) ((Map) map.get("desc")).get("#text")))
                        .build()
                ));
        });

        withClassifications(classificationTypes.toArray(ClassificationType[]::new));

        return this;
    }

    @Override
    public ExtrinsicObjectType build() {
        ExtrinsicObjectType extrinsicObjectType = super.build();
        extrinsicObjectType.setObjectType(DE_STABLE_OBJECT_TYPE);
        return extrinsicObjectType;
    }
}
