package de.servicehealth.epa4all.xds.externalidentifier;

import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExternalIdentifierType;

@SuppressWarnings("unchecked")
public abstract class ExternalIdentifierBuilder<T extends ExternalIdentifierBuilder<T>> {

    public static final String EXTERNAL_IDENTIFIER_OBJECT_TYPE = "urn:oasis:names:tc:ebxml-regrep:ObjectType:RegistryObject:ExternalIdentifier";

    protected String registryObject;
    protected String value;

    public T withRegistryObject(String registryObject) {
        this.registryObject = registryObject;
        return (T) this;
    }

    public T withValue(String value) {
        this.value = value;
        return (T) this;
    }

    public ExternalIdentifierType build() {
        ExternalIdentifierType externalIdentifier = new ExternalIdentifierType();

        externalIdentifier.setObjectType(EXTERNAL_IDENTIFIER_OBJECT_TYPE);
        externalIdentifier.setRegistryObject(registryObject);
        externalIdentifier.setValue(value);
        return externalIdentifier;
    }
}
