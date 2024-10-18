package de.servicehealth.epa4all.server.cetp.mapper.subscription;

import de.gematik.ws.conn.connectorcommon.v5.Status;
import de.gematik.ws.conn.eventservice.v7.SubscriptionRenewal;
import de.health.service.cetp.domain.CetpStatus;
import de.health.service.cetp.domain.SubscriptionResult;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import de.servicehealth.epa4all.server.cetp.mapper.status.StatusMapper;
import de.servicehealth.epa4all.server.cetp.mapper.status.StatusMapperImpl;
import jakarta.xml.ws.Holder;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import javax.xml.datatype.XMLGregorianCalendar;

@Mapper(config = DefaultMappingConfig.class, uses = {StatusMapper.class})
public abstract class SubscriptionResultMapper {

    StatusMapperImpl statusMapper = new StatusMapperImpl();

    @Mapping(source = "subscriptionID", target = "subscriptionId")
    @Mapping(target = "status", ignore = true)
    public abstract SubscriptionResult toDomain(SubscriptionRenewal soap, @Context Holder<Status> status);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "subscriptionId", ignore = true)
    @Mapping(target = "terminationTime", ignore = true)
    public abstract SubscriptionResult toDomain(
        Object emptyInput,
        @Context Status status,
        @Context String subscriptionId,
        @Context XMLGregorianCalendar terminationTime
    );

    @AfterMapping
    public void applyStatus(
        @MappingTarget SubscriptionResult subscriptionResult,
        @Context Holder<Status> status
    ) {
        CetpStatus domain = statusMapper.toDomain(status.value);
        subscriptionResult.setStatus(domain);
    }

    @AfterMapping
    public void applyStatus(
        @MappingTarget SubscriptionResult subscriptionResult,
        @Context Status status,
        @Context String subscriptionId,
        @Context XMLGregorianCalendar terminationTime
    ) {
        CetpStatus domain = statusMapper.toDomain(status);
        subscriptionResult.setStatus(domain);
        subscriptionResult.setSubscriptionId(subscriptionId);
        subscriptionResult.setTerminationTime(terminationTime.toGregorianCalendar().getTime());
    }
}
