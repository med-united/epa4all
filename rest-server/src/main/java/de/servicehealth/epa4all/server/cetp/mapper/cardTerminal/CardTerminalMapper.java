package de.servicehealth.epa4all.server.cetp.mapper.cardTerminal;

import de.gematik.ws.conn.cardterminalinfo.v8.CardTerminalInfoType;
import de.health.service.cetp.domain.eventservice.cardTerminal.CardTerminal;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = DefaultMappingConfig.class, uses = {ProductInformationMapper.class, WorkplaceIdsMapper.class, IPAddressMapper.class})
public interface CardTerminalMapper {

    @Mapping(target = "ipAddress", source = "IPAddress")
    @Mapping(target = "isphysical", source = "ISPHYSICAL")
    CardTerminal toDomain(CardTerminalInfoType soap);
}
