package de.servicehealth.epa4all.xds.classification.ss;

import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;

@Dependent
public class ContentTypeClassificationBuilder extends AbstractSSClassificationBuilder<ContentTypeClassificationBuilder> {

    public static final String CONTENT_TYPE_ID = "author";



    public ClassificationType build() {
        ClassificationType classificationTypeContentType = super.build();
        classificationTypeContentType.setId(CONTENT_TYPE_ID);
        classificationTypeContentType.getSlot().add(createSlotType("codingScheme", getCodingSchema()));

        return classificationTypeContentType;
    }

    @Override
    public String getCodingSchema() {
        return "1.3.6.1.4.1.19376.3.276.1.5.12";
    }
}
