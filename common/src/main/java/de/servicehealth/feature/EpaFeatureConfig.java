package de.servicehealth.feature;

public interface EpaFeatureConfig {

    boolean isMutualTlsEnabled();

    boolean isCetpEnabled();

    boolean isCardlinkEnabled();

    boolean isNativeFhirEnabled();

    boolean isExternalPnwEnabled();
}
