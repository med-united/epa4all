package de.servicehealth.epa4all.server.cetp.mapper.cardTerminal;

import de.health.service.cetp.domain.eventservice.cardTerminal.ProductTypeInformation;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import org.mapstruct.Mapper;

@Mapper(config = DefaultMappingConfig.class)
public interface ProductTypeInformationMapper {

    ProductTypeInformation toDomain(de.gematik.ws._int.version.productinformation.v1.ProductTypeInformation soap);
}
