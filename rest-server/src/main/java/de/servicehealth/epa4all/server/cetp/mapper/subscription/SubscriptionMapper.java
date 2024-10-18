package de.servicehealth.epa4all.server.cetp.mapper.subscription;

import de.health.service.cetp.domain.eventservice.Subscription;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = DefaultMappingConfig.class)
public interface SubscriptionMapper {

    @Mapping(source = "subscriptionID", target = "subscriptionId")
    Subscription toDomain(de.gematik.ws.conn.eventservice.v7.SubscriptionType soap);
}
