package de.servicehealth.epa4all.xds.classification.de;

import de.servicehealth.epa4all.xds.classification.AbstractAuthorPersonClassificationBuilder;
import jakarta.enterprise.context.Dependent;

@Dependent
@DocumentEntry
public class ExtrinsicAuthorClassificationBuilder extends AbstractAuthorPersonClassificationBuilder<ExtrinsicAuthorClassificationBuilder> {

    public static final String EXTRINSIC_AUTHOR_CLASSIFICATION_SCHEME = "urn:uuid:93606bcf-9494-43ec-9b4e-a7748d1a838d";

    @Override
    protected String getClassificationScheme() {
        return EXTRINSIC_AUTHOR_CLASSIFICATION_SCHEME;
    }
}