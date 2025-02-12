package de.servicehealth.epa4all.server.cetp.mapper.cardTerminal;

import de.gematik.ws.conn.eventservice.v7.GetCardTerminalsResponse;
import de.health.service.cetp.domain.eventservice.cardTerminal.CardTerminalsResponse;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import de.servicehealth.epa4all.server.cetp.mapper.status.StatusMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = DefaultMappingConfig.class, uses = {CardTerminalMapper.class, StatusMapper.class})
public interface CardTerminalsResponseMapper {

    @Mapping(target = "cardTerminals", source = "cardTerminals.cardTerminal")
    CardTerminalsResponse toDomain(GetCardTerminalsResponse soap);
}
