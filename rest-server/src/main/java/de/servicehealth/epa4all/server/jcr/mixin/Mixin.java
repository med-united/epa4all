package de.servicehealth.epa4all.server.jcr.mixin;

import de.servicehealth.epa4all.server.jcr.prop.MixinProp;

import java.util.List;

public interface Mixin {

    String getPrefix();

    String getName();

    List<MixinProp> getProperties();
}
