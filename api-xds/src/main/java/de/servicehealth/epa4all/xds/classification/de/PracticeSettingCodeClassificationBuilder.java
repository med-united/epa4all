package de.servicehealth.epa4all.xds.classification.de;

import de.servicehealth.epa4all.xds.classification.ClassificationBuilder;
import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

@Dependent
@DocumentEntry
public class PracticeSettingCodeClassificationBuilder extends ClassificationBuilder<PracticeSettingCodeClassificationBuilder> {

    public static final String PRACTICE_SETTING_CODE_CLASSIFICATION_SCHEME = "urn:uuid:cccf5598-8b07-4b77-a05e-ae952c785ead";

    @Override
    public ClassificationType build() {
        ClassificationType typeCodeClassificationType = super.build();

        typeCodeClassificationType.setId("practiceSettingCode-0");
        typeCodeClassificationType.setClassificationScheme(PRACTICE_SETTING_CODE_CLASSIFICATION_SCHEME);
        return typeCodeClassificationType;
    }

    @Override
    public String getName() {
        return "documentEntry.practiceSettingCode";
    }
}
