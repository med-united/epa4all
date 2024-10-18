package de.servicehealth.epa4all.server.cetp.mapper.event;

import de.gematik.ws.conn.eventservice.v7.EventType;
import de.health.service.cetp.domain.eventservice.event.CetpEventType;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import org.mapstruct.Mapper;

@Mapper(config = DefaultMappingConfig.class)
public interface EventTypeMapper {

    CetpEventType toDomain(EventType eventType);

}
