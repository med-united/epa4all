package de.servicehealth.epa4all.xds.classification.de;

import de.servicehealth.epa4all.xds.classification.ClassificationBuilder;
import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

@Dependent
@DocumentEntry
public class ClassCodeClassificationBuilder extends ClassificationBuilder<ClassCodeClassificationBuilder> {

    public static final String CLASS_CODE_CLASSIFICATION_SCHEME = "urn:uuid:41a5887f-8865-4c09-adf7-e362475b143a";

    @Override
    public ClassificationType build() {
        ClassificationType classCodeclassificationType = super.build();

        classCodeclassificationType.setId("classCode-0");
        classCodeclassificationType.setClassificationScheme(CLASS_CODE_CLASSIFICATION_SCHEME);

        return classCodeclassificationType;
    }

    @Override
    public String getName() {
        return "documentEntry.classCode";
    }
}
