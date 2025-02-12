package de.servicehealth.epa4all.server.cetp.mapper.cardTerminal;

import de.health.service.cetp.domain.eventservice.cardTerminal.ProductInformation;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import org.mapstruct.Mapper;

@Mapper(config = DefaultMappingConfig.class, uses = {ProductTypeInformationMapper.class, ProductIdentificationMapper.class, ProductMiscellaneousMapper.class})
public interface ProductInformationMapper {

    ProductInformation toDomain(de.gematik.ws._int.version.productinformation.v1.ProductInformation soap);
}
