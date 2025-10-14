package de.servicehealth.epa4all.xds.classification.ss;

import de.servicehealth.epa4all.xds.classification.ClassificationBuilder;
import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

@Dependent
@SubmissionSet
public class ContentTypeClassificationBuilder extends ClassificationBuilder<ContentTypeClassificationBuilder> {

    public static final String CONTENT_TYPE_ID = "author";

    public ClassificationType build() {
        ClassificationType classificationTypeContentType = super.build();
        classificationTypeContentType.setId(CONTENT_TYPE_ID);

        return classificationTypeContentType;
    }

    @Override
    public String getName() {
        return "1.3.6.1.4.1.19376.3.276.1.5.12";
    }
}