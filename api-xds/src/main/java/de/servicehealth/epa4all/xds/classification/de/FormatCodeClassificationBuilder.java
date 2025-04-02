package de.servicehealth.epa4all.xds.classification.de;

import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

import static de.servicehealth.epa4all.xds.XDSUtils.isXmlCompliant;

@Dependent
public class FormatCodeClassificationBuilder extends AbstractDEClassificationBuilder<FormatCodeClassificationBuilder> {

    public static final String FORMAT_CODE_CLASSIFICATION_SCHEME = "urn:uuid:a09d5840-386c-46f2-b5ad-9c3699a4309d";
    public static final String MIME_TYPE_SUFFICIENT_REPRESENTATION = "urn:ihe:iti:xds:2017:mimeTypeSufficient";

    @Override
    public String getName() {
        return "documentEntry.formatCode";
    }

    @Override
    public FormatCodeClassificationBuilder withNodeRepresentation(String nodeRepresentation) {
        if (mimeType == null || isXmlCompliant(mimeType)) {
            return super.withNodeRepresentation(nodeRepresentation);
        } else {
            super.withNodeRepresentation("urn:ihe:iti:xds-sd:pdf:2008");
        }
        return this;
    }

    @Override
    public ClassificationType build() {
        withNodeRepresentation(MIME_TYPE_SUFFICIENT_REPRESENTATION);

        ClassificationType formatCodeClassificationType = super.build();

        formatCodeClassificationType.setId("formatCode-0");
        formatCodeClassificationType.setClassificationScheme(FORMAT_CODE_CLASSIFICATION_SCHEME);

        return formatCodeClassificationType;
    }
}
