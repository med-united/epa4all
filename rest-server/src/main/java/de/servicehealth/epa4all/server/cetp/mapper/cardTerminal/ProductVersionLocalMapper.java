package de.servicehealth.epa4all.server.cetp.mapper.cardTerminal;

import de.health.service.cetp.domain.eventservice.cardTerminal.ProductVersionLocal;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = DefaultMappingConfig.class)
public interface ProductVersionLocalMapper {

    @Mapping(target = "hwVersion", source = "HWVersion")
    @Mapping(target = "fwVersion", source = "FWVersion")
    ProductVersionLocal toDomain(de.gematik.ws._int.version.productinformation.v1.ProductVersionLocal soap);
}
