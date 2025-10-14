package de.servicehealth.epa4all.xds.classification;

import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.InternationalStringType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.LocalizedStringType;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;

@SuppressWarnings("unchecked")
public abstract class ClassificationBuilder<T extends ClassificationBuilder<T>> {

    public static final String CLASSIFICATION_OBJECT_TYPE = "urn:oasis:names:tc:ebxml-regrep:ObjectType:RegistryObject:Classification";

    private String classifiedObject;

    protected LocalizedStringType localizedString;
    protected String nodeRepresentation;
    protected String mimeType;
    protected String codingScheme;

    public abstract String getName();

    public T withLocalizedString(LocalizedStringType localizedString) {
        this.localizedString = localizedString;
        return (T) this;
    }

    public T withNodeRepresentation(String nodeRepresentation) {
        this.nodeRepresentation = nodeRepresentation;
        return (T) this;
    }

    public T withMimeType(String mimeType) {
        this.mimeType = mimeType;
        return (T) this;
    }

    public T withClassifiedObject(String classifiedObject) {
        this.classifiedObject = classifiedObject;
        return (T) this;
    }

    public T withCodingScheme(String codingScheme) {
        this.codingScheme = codingScheme;
        return (T) this;
    }

    public ClassificationType build() {
        ClassificationType classificationType = new ClassificationType();
        classificationType.setObjectType(CLASSIFICATION_OBJECT_TYPE);
        classificationType.setClassifiedObject(classifiedObject);

        if (localizedString != null) {
            classificationType.setName(new InternationalStringType());
            classificationType.getName().getLocalizedString().add(localizedString);
        }
        if (nodeRepresentation != null) {
            classificationType.setNodeRepresentation(nodeRepresentation);
        }
        if (codingScheme != null) {
            classificationType.getSlot().add(createSlotType("codingScheme", codingScheme));
        }

        return classificationType;
    }
}
