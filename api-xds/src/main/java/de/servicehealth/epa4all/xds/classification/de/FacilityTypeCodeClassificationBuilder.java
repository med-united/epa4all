package de.servicehealth.epa4all.xds.classification.de;

import de.servicehealth.epa4all.xds.classification.ClassificationBuilder;
import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;

@Dependent
public class FacilityTypeCodeClassificationBuilder extends AbstractDEClassificationBuilder<FacilityTypeCodeClassificationBuilder> {

    public static final String FACILITY_TYPE_CODE_CLASSIFICATION_SCHEME = "urn:uuid:f33fb8ac-18af-42cc-ae0e-ed0b0bdb91e1";

    @Override
    public ClassificationType build() {
        ClassificationType typeCodeClassificationType = super.build();

        typeCodeClassificationType.setId("facilityTypeCode-0");
        typeCodeClassificationType.setClassificationScheme(FACILITY_TYPE_CODE_CLASSIFICATION_SCHEME);
        typeCodeClassificationType.getSlot().add(createSlotType("codingScheme", getCodingSchema()));
        return typeCodeClassificationType;
    }

    @Override
    public String getCodingSchema() {
        return "1.3.6.1.4.1.19376.3.276.1.5.2";
    }
}