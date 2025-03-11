package de.servicehealth.epa4all.server.jcr.mixin;

import de.servicehealth.epa4all.server.jcr.prop.MixinProp;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.birthday;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.displayname;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.entryuuid;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.firstname;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.getlastmodified;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.lastname;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.smcb;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.validto;
import static de.servicehealth.epa4all.server.jcr.prop.MixinProp.EPA_NAMESPACE_PREFIX;

@ApplicationScoped
public class EpaCustom implements Mixin {

    public static final String EPA_MIXIN_NAME = "epa:custom";

    @Override
    public String getPrefix() {
        return EPA_NAMESPACE_PREFIX;
    }

    @Override
    public String getName() {
        return EPA_MIXIN_NAME;
    }

    @Override
    public List<MixinProp> getProperties() {
        return List.of(
            firstname,
            lastname,
            birthday,
            displayname,
            validto,
            getlastmodified,
            smcb,
            entryuuid
        );
    }
}
