package de.servicehealth.epa4all.server.cetp.mapper.cardTerminal;

import de.health.service.cetp.domain.eventservice.cardTerminal.ProductIdentification;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import org.mapstruct.Mapper;

@Mapper(config = DefaultMappingConfig.class, uses = {ProductVersionMapper.class})
public interface ProductIdentificationMapper {

    ProductIdentification toDomain(de.gematik.ws._int.version.productinformation.v1.ProductIdentification soap);
}
