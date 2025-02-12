package de.servicehealth.epa4all.server.cetp.mapper.cardTerminal;

import de.health.service.cetp.domain.eventservice.cardTerminal.WorkplaceIds;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import org.mapstruct.Mapper;

@Mapper(config = DefaultMappingConfig.class)
public interface WorkplaceIdsMapper {

    WorkplaceIds toDomain(de.gematik.ws.conn.connectorcommon.v5.WorkplaceIds soap);
}
