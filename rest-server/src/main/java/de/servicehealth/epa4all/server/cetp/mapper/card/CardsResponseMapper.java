package de.servicehealth.epa4all.server.cetp.mapper.card;

import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import de.health.service.cetp.domain.eventservice.card.CardsResponse;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import de.servicehealth.epa4all.server.cetp.mapper.status.StatusMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = DefaultMappingConfig.class, uses = {CardMapper.class, StatusMapper.class})
public interface CardsResponseMapper {

    @Mapping(target = "cards", source = "cards.card")
    CardsResponse toDomain(GetCardsResponse soap);
}
