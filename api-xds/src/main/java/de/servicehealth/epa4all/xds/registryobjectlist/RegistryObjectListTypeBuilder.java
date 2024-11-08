package de.servicehealth.epa4all.xds.registryobjectlist;

import jakarta.xml.bind.JAXBElement;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.AssociationType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.IdentifiableType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;

import javax.xml.namespace.QName;

public class RegistryObjectListTypeBuilder {

    private static final String NAMESPACE = "urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0";

    private RegistryPackageType registryPackageType;
    private ExtrinsicObjectType extrinsicObjectType;
    private AssociationType1 associationType1;

    public RegistryObjectListTypeBuilder withRegistryPackageType(RegistryPackageType registryPackageType) {
        this.registryPackageType = registryPackageType;
        return this;
    }

    public RegistryObjectListTypeBuilder withExtrinsicObjectType(ExtrinsicObjectType extrinsicObjectType) {
        this.extrinsicObjectType = extrinsicObjectType;
        return this;
    }

    public RegistryObjectListTypeBuilder withAssociationType1(AssociationType1 associationType1) {
        this.associationType1 = associationType1;
        return this;
    }

    private <T extends IdentifiableType> void addIdentifiable(
        RegistryObjectListType registryObjectListType,
        Class<T> clazz,
        T identifiable,
        String name
    ) {
        JAXBElement<T> registryPackage = new JAXBElement<>(
            new QName(NAMESPACE, name), clazz, identifiable
        );
        registryObjectListType.getIdentifiable().add(registryPackage);
    }

    public RegistryObjectListType build() {
        RegistryObjectListType registryObjectListType = new RegistryObjectListType();

        addIdentifiable(registryObjectListType, RegistryPackageType.class, registryPackageType, "RegistryPackage");
        addIdentifiable(registryObjectListType, ExtrinsicObjectType.class, extrinsicObjectType, "ExtrinsicObject");
        addIdentifiable(registryObjectListType, AssociationType1.class, associationType1, "Association");

        return registryObjectListType;
    }
}
