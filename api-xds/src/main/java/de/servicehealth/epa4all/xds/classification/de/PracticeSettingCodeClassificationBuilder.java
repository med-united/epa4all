package de.servicehealth.epa4all.xds.classification.de;

import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;

@Dependent
public class PracticeSettingCodeClassificationBuilder extends AbstractDEClassificationBuilder<PracticeSettingCodeClassificationBuilder> {

    public static final String PRACTICE_SETTING_CODE_CLASSIFICATION_SCHEME = "urn:uuid:cccf5598-8b07-4b77-a05e-ae952c785ead";

    @Override
    public ClassificationType build() {
        ClassificationType typeCodeClassificationType = super.build();

        typeCodeClassificationType.setId("practiceSettingCode-0");
        typeCodeClassificationType.setClassificationScheme(PRACTICE_SETTING_CODE_CLASSIFICATION_SCHEME);
        typeCodeClassificationType.getSlot().add(createSlotType("codingScheme", getCodingSchema()));
        return typeCodeClassificationType;
    }

    @Override
    public String getCodingSchema() {
        return "1.3.6.1.4.1.19376.3.276.1.5.4";
    }
}
