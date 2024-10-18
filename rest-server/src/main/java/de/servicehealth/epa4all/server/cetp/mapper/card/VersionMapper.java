package de.servicehealth.epa4all.server.cetp.mapper.card;

import de.gematik.ws.conn.cardservice.v8.VersionInfoType;
import de.health.service.cetp.domain.eventservice.card.VersionInfo;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import org.mapstruct.Mapper;

@Mapper(config = DefaultMappingConfig.class)
public interface VersionMapper {

    VersionInfo toDomain(VersionInfoType soap);
}
