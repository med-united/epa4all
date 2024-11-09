package de.servicehealth.epa4all.xds.association;

public enum AssociationType {

    Membership,
    Replace,
    Transformation,
    Addendum,
    ReplaceWithTransformation,
    DigitalSignature,
    Snapshot;

    public String value() {
        return switch (this) {
            case Membership -> "urn:oasis:names:tc:ebxml-regrep:AssociationType:HasMember";
            case Replace -> "urn:ihe:iti:2007:AssociationType:RPLC";
            case Transformation -> "urn:ihe:iti:2007:AssociationType:XFRM";
            case Addendum -> "urn:ihe:iti:2007:AssociationType:APND";
            case ReplaceWithTransformation -> "urn:ihe:iti:2007:AssociationType:XFRM_RPLC";
            case DigitalSignature -> "urn:ihe:iti:2007:AssociationType:signs";
            case Snapshot -> "urn:ihe:iti:2010:AssociationType:IsSnapshotOf";
        };
    }
}
