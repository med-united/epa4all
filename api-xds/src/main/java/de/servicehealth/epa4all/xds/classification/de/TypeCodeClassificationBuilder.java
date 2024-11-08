package de.servicehealth.epa4all.xds.classification.de;

import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

@Dependent
public class TypeCodeClassificationBuilder extends AbstractDEClassificationBuilder<TypeCodeClassificationBuilder> {

    public static final String TYPE_CODE_CLASSIFICATION_SCHEME = "urn:uuid:f0306f51-975f-434e-a61c-c59651d33983";

    @Override
    public ClassificationType build() {
        ClassificationType typeCodeClassificationType = super.build();

        typeCodeClassificationType.setId("typeCode-0");
        typeCodeClassificationType.setClassificationScheme(TYPE_CODE_CLASSIFICATION_SCHEME);
        return typeCodeClassificationType;
    }

    @Override
    public String getName() {
        return "documentEntry.typeCode";
    }
}