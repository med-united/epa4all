package de.servicehealth.epa4all.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class JcrExtensionProcessor {
    
    private static final String FEATURE = "lib-jcr";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}