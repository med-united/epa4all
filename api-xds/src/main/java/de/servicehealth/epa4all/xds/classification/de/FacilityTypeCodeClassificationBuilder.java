package de.servicehealth.epa4all.xds.classification.de;

import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

@Dependent
public class FacilityTypeCodeClassificationBuilder extends AbstractDEClassificationBuilder<FacilityTypeCodeClassificationBuilder> {

    public static final String FACILITY_TYPE_CODE_CLASSIFICATION_SCHEME = "urn:uuid:f33fb8ac-18af-42cc-ae0e-ed0b0bdb91e1";

    @Override
    public ClassificationType build() {
        ClassificationType typeCodeClassificationType = super.build();

        typeCodeClassificationType.setId("facilityTypeCode-0");
        typeCodeClassificationType.setClassificationScheme(FACILITY_TYPE_CODE_CLASSIFICATION_SCHEME);

        return typeCodeClassificationType;
    }

    @Override
    public String getName() {
        return "documentEntry.healthcareFacilityTypeCode";
    }
}