package de.servicehealth.feature;

public interface EpaFeatureConfig {

    boolean isMutualTlsEnabled();

    boolean isCetpEnabled();

    boolean isPoppEnabled();

    boolean isCardlinkEnabled();

    boolean isNativeFhirEnabled();

    boolean isExternalPnwEnabled();
}
