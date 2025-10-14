package de.servicehealth.epa4all.xds.classification.ss;

import de.servicehealth.epa4all.xds.classification.AbstractAuthorPersonClassificationBuilder;
import jakarta.enterprise.context.Dependent;

@Dependent
@SubmissionSet
public class PackageAuthorPersonClassificationBuilder extends AbstractAuthorPersonClassificationBuilder<PackageAuthorPersonClassificationBuilder> {

    public static final String SUBMISSION_SET_AUTHOR_CLASSIFICATION_SCHEME = "urn:uuid:a7058bb9-b4e4-4307-ba5b-e3f0ab85e12d";

    @Override
    protected String getClassificationScheme() {
        return SUBMISSION_SET_AUTHOR_CLASSIFICATION_SCHEME;
    }
}