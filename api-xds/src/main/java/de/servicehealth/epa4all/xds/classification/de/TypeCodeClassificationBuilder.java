package de.servicehealth.epa4all.xds.classification.de;

import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;

@Dependent
public class TypeCodeClassificationBuilder extends AbstractDEClassificationBuilder<TypeCodeClassificationBuilder> {

    public static final String TYPE_CODE_CLASSIFICATION_SCHEME = "urn:uuid:f0306f51-975f-434e-a61c-c59651d33983";

    @Override
    public ClassificationType build() {
        ClassificationType typeCodeClassificationType = super.build();

        typeCodeClassificationType.setId("typeCode-0");
        typeCodeClassificationType.setClassificationScheme(TYPE_CODE_CLASSIFICATION_SCHEME);
        typeCodeClassificationType.getSlot().add(createSlotType("codingScheme", getCodingSchema()));
        return typeCodeClassificationType;
    }

    @Override
    public String getCodingSchema() {
        return "1.3.6.1.4.1.19376.3.276.1.5.9";
    }
}