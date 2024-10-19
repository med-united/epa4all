package de.servicehealth.epa4all.server.cetp.mapper.status;

import de.gematik.ws.conn.connectorcommon.v5.Status;
import de.health.service.cetp.domain.CetpStatus;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import org.mapstruct.Mapper;

@Mapper(config = DefaultMappingConfig.class, uses = {ErrorMapper.class})
public interface StatusMapper {

    CetpStatus toDomain(Status soap);
}
