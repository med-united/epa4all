package de.servicehealth.epa4all.server.cetp.mapper.status;

import de.health.service.cetp.domain.fault.Trace;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = DefaultMappingConfig.class, uses = {DetailMapper.class})
public interface TraceMapper {

    @Mapping(source = "eventID", target = "eventId")
    Trace toDomain(de.gematik.ws.tel.error.v2.Error.Trace soap);
}
