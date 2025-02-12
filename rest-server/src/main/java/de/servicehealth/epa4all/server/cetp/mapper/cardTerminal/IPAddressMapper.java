package de.servicehealth.epa4all.server.cetp.mapper.cardTerminal;

import de.gematik.ws.conn.cardterminalinfo.v8.CardTerminalInfoType;
import de.health.service.cetp.domain.eventservice.cardTerminal.IPAddress;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = DefaultMappingConfig.class)
public interface IPAddressMapper {

    @Mapping(target = "ipv4Address", source = "IPV4Address")
    @Mapping(target = "ipv6Address", source = "IPV6Address")
    IPAddress toDomain(CardTerminalInfoType.IPAddress soap);
}
