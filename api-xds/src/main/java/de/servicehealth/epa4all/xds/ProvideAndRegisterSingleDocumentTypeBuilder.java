package de.servicehealth.epa4all.xds;

import de.servicehealth.epa4all.xds.association.AssociationType;
import de.servicehealth.epa4all.xds.association.SSDEAssociationBuilder;
import de.servicehealth.epa4all.xds.author.AuthorPerson;
import de.servicehealth.epa4all.xds.ebrim.FolderDefinition;
import de.servicehealth.epa4all.xds.externalidentifier.de.DEPatientIdExternalIdentifierBuilder;
import de.servicehealth.epa4all.xds.externalidentifier.de.DEUniqueIdExternalIdentifierBuilder;
import de.servicehealth.epa4all.xds.externalidentifier.ss.SSPatientIdExternalIdentifierBuilder;
import de.servicehealth.epa4all.xds.externalidentifier.ss.SSUniqueIdExternalIdentifierBuilder;
import de.servicehealth.epa4all.xds.extrinsic.StableDocumentEntryBuilder;
import de.servicehealth.epa4all.xds.registryobjectlist.RegistryObjectListTypeBuilder;
import de.servicehealth.epa4all.xds.registrypackage.RegistryPackageBuilder;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.AssociationType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static de.servicehealth.epa4all.xds.XDSUtils.generateOID;
import static de.servicehealth.epa4all.xds.XDSUtils.generateUrnUuid;
import static de.servicehealth.epa4all.xds.XDSUtils.isPdfCompliant;
import static de.servicehealth.epa4all.xds.XDSUtils.isXmlCompliant;

@Dependent
public class ProvideAndRegisterSingleDocumentTypeBuilder extends ProvideAndRegisterDocumentBuilder {

    @Inject
    RegistryPackageBuilder registryPackageBuilder;

    @Inject
    StableDocumentEntryBuilder stableDocumentEntryBuilder;

    @Inject
    SSDEAssociationBuilder ssdeAssociationBuilder;

    private ProvideAndRegisterDocumentSetRequestType.Document document;
    private List<FolderDefinition> folderDefinitions;
    private AuthorPerson authorPerson;
    private String telematikId;
    private String documentId;
    private String fileName;
    private String title;
    private String praxis;
    private String practiceSetting;
    private String information;
    private String information2;
    private String contentType;
    private String languageCode;
    private String kvnr;

    public void init(
        ProvideAndRegisterDocumentSetRequestType.Document document,
        List<FolderDefinition> folderDefinitions,
        AuthorPerson authorPerson,
        String telematikId,
        String documentId,
        String fileName,
        String title,
        String praxis,
        String practiceSetting,
        String information,
        String information2,
        String contentType,
        String languageCode,
        String kvnr
    ) {
        this.document = document;
        this.documentId = documentId;
        this.telematikId = telematikId;
        this.title = title;
        this.praxis = praxis;
        this.practiceSetting = practiceSetting;
        this.information = information;
        this.information2 = information2;
        this.fileName = fileName;
        this.contentType = contentType;
        this.languageCode = languageCode;
        this.authorPerson = authorPerson;
        this.kvnr = kvnr;
        this.folderDefinitions = new ArrayList<>(folderDefinitions);
    }

    @Override
    public ProvideAndRegisterDocumentSetRequestType build() {
        withDocument(document);

        String submissionSetId = generateUrnUuid();
        String associationId = generateUrnUuid();

        String ssPatientId = UUID.randomUUID().toString();
        String ssUniqueId = UUID.randomUUID().toString();
        String dePatientId = UUID.randomUUID().toString();
        String deUniqueId = UUID.randomUUID().toString(); // TODO perhaps we could store id for further document lookup

        String uniqueIdValue = generateOID(); // TODO verify

        String patientExternalIdValue = kvnr + "^^^&1.2.276.0.76.4.8&ISO";
        RegistryPackageType registryPackageType = registryPackageBuilder
            .withTelematikId(telematikId)
            .withSubmissionSetId(submissionSetId)
            .withAuthorPerson(authorPerson)
            .withExternalIdentifiers(
                new SSPatientIdExternalIdentifierBuilder(ssPatientId).withRegistryObject(submissionSetId).withValue(patientExternalIdValue).build(),
                new SSUniqueIdExternalIdentifierBuilder(ssUniqueId).withValue(uniqueIdValue).withRegistryObject(submissionSetId).build()
            )
            .build();

        String value;
        if (isXmlCompliant(contentType)) {
            value = uniqueIdValue + ".xml";
        } else if (isPdfCompliant(contentType)) {
            value = uniqueIdValue + ".pdf";
        } else {
            value = uniqueIdValue;
        }

        ExtrinsicObjectType extrinsicObjectType = stableDocumentEntryBuilder
            .withDocumentId(documentId)
            .withLanguageCode(languageCode)
            .withMimeType(contentType)
            .withUniqueId(value)
            .withValue(title == null ? fileName : title)
            .withExternalIdentifiers(
                new DEPatientIdExternalIdentifierBuilder(dePatientId).withRegistryObject(documentId).withValue(patientExternalIdValue).build(),
                new DEUniqueIdExternalIdentifierBuilder(deUniqueId).withValue(uniqueIdValue).withRegistryObject(documentId).build()
            )
            // calls withClassifications() internally
            .finalize(contentType, authorPerson, praxis, practiceSetting, information, information2, folderDefinitions)
            .build();

        AssociationType1 associationType1 = ssdeAssociationBuilder
            .withAssociationId(associationId)
            .newDocument(true)
            .withSourceObject(submissionSetId)
            .withTargetObject(documentId)
            .withAssociationType(AssociationType.Membership)
            .build();

        RegistryObjectListType registryObjectListType = new RegistryObjectListTypeBuilder()
            .withRegistryPackageType(registryPackageType)
            .withExtrinsicObjectType(extrinsicObjectType)
            .withAssociationType1(associationType1)
            .build();

        withRegistryObjectListType(registryObjectListType);

        return super.build();
    }
}