package de.servicehealth.epa4all.xds.association;

import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.AssociationType1;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;

@Dependent
public class SSDEAssociationBuilder {

    private boolean newDocument;
    private AssociationType associationType;
    private String sourceObject;
    private String targetObject;

    private String associationId;

    public SSDEAssociationBuilder withAssociationId(String associationId) {
        this.associationId = associationId;
        return this;
    }

    public SSDEAssociationBuilder withSourceObject(String sourceObject) {
        this.sourceObject = sourceObject;
        return this;
    }

    public SSDEAssociationBuilder withTargetObject(String targetObject) {
        this.targetObject = targetObject;
        return this;
    }

    public SSDEAssociationBuilder withAssociationType(AssociationType associationType) {
        this.associationType = associationType;
        return this;
    }

    public SSDEAssociationBuilder newDocument(boolean newDocument) {
        this.newDocument = newDocument;
        return this;
    }

    public AssociationType1 build() {
        AssociationType1 associationType1 = new AssociationType1();
        associationType1.setAssociationType(associationType.value());
        associationType1.setId(associationId);
        associationType1.setSourceObject(sourceObject);
        associationType1.setTargetObject(targetObject);
        associationType1.getSlot().add(createSlotType("SubmissionSetStatus", newDocument ? "Original" : "Reference"));
        return associationType1;
    }
}
