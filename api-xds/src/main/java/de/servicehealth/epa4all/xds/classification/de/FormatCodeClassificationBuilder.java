package de.servicehealth.epa4all.xds.classification.de;

import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;

@Dependent
public class FormatCodeClassificationBuilder extends AbstractDEClassificationBuilder<FormatCodeClassificationBuilder> {

    public static final String FORMAT_CODE_CLASSIFICATION_SCHEME = "urn:uuid:a09d5840-386c-46f2-b5ad-9c3699a4309d";
    public static final String MIME_TYPE_SUFFICIENT_REPRESENTATION = "urn:ihe:iti:xds:2017:mimeTypeSufficient";

    @Override
    public String getCodingSchema() {
        return "1.3.6.1.4.1.19376.1.2.3";
    }

    @Override
    public FormatCodeClassificationBuilder withNodeRepresentation(String nodeRepresentation) {
        if (mimeType == null || mimeType.equalsIgnoreCase("application/xml")) {
            return super.withNodeRepresentation(nodeRepresentation);
        } else {
            withNodeRepresentation(MIME_TYPE_SUFFICIENT_REPRESENTATION);
        }
        return this;
    }

    @Override
    public ClassificationType build() {
        withNodeRepresentation("urn:ihe:iti:xds:2017:mimeTypeSufficient");

        ClassificationType formatCodeClassificationType = super.build();

        formatCodeClassificationType.setId("formatCode-0");
        formatCodeClassificationType.setClassificationScheme(FORMAT_CODE_CLASSIFICATION_SCHEME);
        formatCodeClassificationType.getSlot().add(createSlotType("codingScheme", getCodingSchema()));

        return formatCodeClassificationType;
    }
}
