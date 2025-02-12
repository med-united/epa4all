package de.servicehealth.epa4all.server.cetp.mapper.cardTerminal;

import de.health.service.cetp.domain.eventservice.cardTerminal.ProductVersion;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import org.mapstruct.Mapper;

@Mapper(config = DefaultMappingConfig.class, uses = {ProductVersionLocalMapper.class})
public interface ProductVersionMapper {

    ProductVersion toDomain(de.gematik.ws._int.version.productinformation.v1.ProductVersion soap);
}
