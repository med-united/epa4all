package de.servicehealth.epa4all.xds.registrypackage;

import de.servicehealth.epa4all.xds.author.AuthorPerson;
import de.servicehealth.epa4all.xds.classification.ss.PackageAuthorPersonClassificationBuilder;
import de.servicehealth.epa4all.xds.classification.ss.SubmissionSet;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExternalIdentifierType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ValueListType;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static de.servicehealth.epa4all.xds.XDSUtils.getNumericISO8601Timestamp;
import static de.servicehealth.epa4all.xds.classification.ClassificationBuilder.CLASSIFICATION_OBJECT_TYPE;

@Dependent
public class RegistryPackageBuilder {

    private static final String REGISTRY_PACKAGE_OBJECT_TYPE = "urn:oasis:names:tc:ebxml-regrep:ObjectType:RegistryObject:RegistryPackage";
    private static final String SS_CLASSIFICATION_NODE = "urn:uuid:a54d6aa5-d40d-43f9-88c5-b4633d873bdd";

    private String telematikId;
    private String id;

    @Inject
    @SubmissionSet
    PackageAuthorPersonClassificationBuilder packageAuthorPersonClassificationBuilder;

    private AuthorPerson authorPerson;
    private String authorInstitution;
    private ClassificationType[] classificationTypes;
    private ExternalIdentifierType[] externalIdentifierTypes;

    public RegistryPackageBuilder withTelematikId(String telematikId) {
        this.telematikId = telematikId;
        return this;
    }

    public RegistryPackageBuilder withSubmissionSetId(String submissionSetId) {
        this.id = submissionSetId;
        return this;
    }

    public RegistryPackageBuilder withClassifications(ClassificationType... classificationTypes) {
        this.classificationTypes = classificationTypes;
        return this;
    }

    public RegistryPackageBuilder withExternalIdentifiers(ExternalIdentifierType... externalIdentifierTypes) {
        this.externalIdentifierTypes = externalIdentifierTypes;
        return this;
    }

    public RegistryPackageBuilder withAuthorPerson(AuthorPerson authorPerson) {
        this.authorPerson = authorPerson;
        return this;
    }

    public RegistryPackageBuilder withAuthorInstitution(String authorInstitution) {
        this.authorInstitution = authorInstitution;
        return this;
    }

    public RegistryPackageType build() {
        RegistryPackageType registryPackageType = new RegistryPackageType();

        registryPackageType.setObjectType(REGISTRY_PACKAGE_OBJECT_TYPE);
        registryPackageType.setId(id);

        applySubmissionTime(registryPackageType);
        applySSClassificationNode(registryPackageType);

        if (classificationTypes != null) {
            Stream.of(classificationTypes).forEach(registryPackageType.getClassification()::add);
        }
        if (externalIdentifierTypes != null) {
            Stream.of(externalIdentifierTypes).forEach(registryPackageType.getExternalIdentifier()::add);
        }

        if (authorPerson != null) {
            ClassificationType authorClassificationType = packageAuthorPersonClassificationBuilder
                .withClassifiedObject(id)
                .withTelematikId(telematikId)
                .withAuthorPerson(authorPerson)
                .withAuthorInstitution(authorInstitution)
                .build();
            registryPackageType.getClassification().add(authorClassificationType);
        }

        return registryPackageType;
    }

    private void applySubmissionTime(RegistryPackageType registryPackageType) {
        SlotType1 submissionTime = new SlotType1();
        submissionTime.setName("submissionTime");
        submissionTime.setValueList(new ValueListType());
        submissionTime.getValueList().getValue().add(getNumericISO8601Timestamp(LocalDateTime.now()));
        registryPackageType.getSlot().add(submissionTime);
    }

    private void applySSClassificationNode(RegistryPackageType registryPackageType) {
        ClassificationType classificationTypeRegistryPackage = new ClassificationType();
        classificationTypeRegistryPackage.setClassifiedObject(id);
        classificationTypeRegistryPackage.setId("SubmissionSetClassification");
        classificationTypeRegistryPackage.setClassificationNode(SS_CLASSIFICATION_NODE);
        classificationTypeRegistryPackage.setObjectType(CLASSIFICATION_OBJECT_TYPE);
        registryPackageType.getClassification().add(classificationTypeRegistryPackage);
    }
}
