package de.servicehealth.epa4all.xds.classification.de;

import de.servicehealth.epa4all.xds.classification.ClassificationBuilder;

public abstract class AbstractDEClassificationBuilder<T extends AbstractDEClassificationBuilder<T>> extends ClassificationBuilder<T> {

    @Override
    public String getCodingSchemaType() {
        return "DE";
    }
}
