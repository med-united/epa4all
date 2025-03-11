package de.servicehealth.epa4all.server.jcr.mixin;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class MixinsProvider {

    @Inject
    @Any
    Instance<Mixin> mixins;

    public List<Mixin> getCurrentMixins() {
        return mixins.stream().toList();
    }
}