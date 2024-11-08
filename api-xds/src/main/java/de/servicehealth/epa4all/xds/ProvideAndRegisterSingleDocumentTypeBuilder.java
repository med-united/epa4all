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
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.AssociationType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;

import java.util.ArrayList;
import java.util.List;

import static de.servicehealth.epa4all.xds.XDSUtils.generateOID;

@RequestScoped
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
    private String contentType;
    private String languageCode;
    private String kvnr;

    public void init(
        ProvideAndRegisterDocumentSetRequestType.Document document,
        List<FolderDefinition> folderDefinitions,
        AuthorPerson authorPerson,
        String telematikId,
        String documentId,
        String languageCode,
        String contentType,
        String kvnr
    ) {
        this.document = document;
        this.documentId = documentId;
        this.telematikId = telematikId;
        this.contentType = contentType;
        this.languageCode = languageCode;
        this.authorPerson = authorPerson;
        this.kvnr = kvnr;
        this.folderDefinitions = new ArrayList<>(folderDefinitions);
    }

    @Override
    public ProvideAndRegisterDocumentSetRequestType build() {
        withDocument(document);

        String submissionSetId = "submissionSet-0"; // TODO - should be set outside?
        String associationId = "association-0";

        String ssPatientId = "ss-patientId-0";
        String ssUniqueId = "ss-uniqueId-0";
        String dePatientId = "de-patientId-0";
        String deUniqueId = "de-uniqueId-0";

        String uniqueIdValue = generateOID();

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

        ExtrinsicObjectType extrinsicObjectType = stableDocumentEntryBuilder
            .withDocumentId(documentId)
            .withLanguageCode(languageCode)
            .withMimeType(contentType)
            .withUniqueId(uniqueIdValue + ".xml")
            .withValue("Dokument " + uniqueIdValue)
            .withExternalIdentifiers(
                new DEPatientIdExternalIdentifierBuilder(dePatientId).withRegistryObject(documentId).withValue(patientExternalIdValue).build(),
                new DEUniqueIdExternalIdentifierBuilder(deUniqueId).withValue(uniqueIdValue).withRegistryObject(documentId).build()
            )
            .finalize(authorPerson, folderDefinitions) // calls withClassifications() internally
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
