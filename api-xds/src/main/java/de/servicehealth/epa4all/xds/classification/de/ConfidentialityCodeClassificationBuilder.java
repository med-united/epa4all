package de.servicehealth.epa4all.xds.classification.de;

import de.servicehealth.epa4all.xds.classification.ClassificationBuilder;
import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;

@Dependent
public class ConfidentialityCodeClassificationBuilder extends AbstractDEClassificationBuilder<ConfidentialityCodeClassificationBuilder> {

    public static final String CONFIDENTIALITY_CODE_CLASSIFICATION_SCHEME = "urn:uuid:f4f85eac-e6cb-4883-b524-f2705394840f";

    @Override
    public ClassificationType build() {
        ClassificationType classCodeclassificationType = super.build();

        classCodeclassificationType.setId("confidentiality-0");
        classCodeclassificationType.setClassificationScheme(CONFIDENTIALITY_CODE_CLASSIFICATION_SCHEME);
        classCodeclassificationType.getSlot().add(createSlotType("codingScheme", getCodingSchema()));

        return classCodeclassificationType;
    }

    @Override
    public String getCodingSchema() {
        return "1.2.276.0.76.5.491";
    }
}
