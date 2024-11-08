package de.servicehealth.epa4all.xds.classification.ss;

import de.servicehealth.epa4all.xds.classification.ClassificationBuilder;

public abstract class AbstractSSClassificationBuilder<T extends AbstractSSClassificationBuilder<T>> extends ClassificationBuilder<T> {

    @Override
    public String getCodingSchemaType() {
        return "SS";
    }
}