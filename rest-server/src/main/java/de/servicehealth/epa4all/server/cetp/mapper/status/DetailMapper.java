package de.servicehealth.epa4all.server.cetp.mapper.status;

import de.health.service.cetp.domain.fault.Detail;
import de.servicehealth.epa4all.server.cetp.mapper.DefaultMappingConfig;
import org.mapstruct.Mapper;

@Mapper(config = DefaultMappingConfig.class)
public interface DetailMapper {

    Detail toDomain(de.gematik.ws.tel.error.v2.Error.Trace.Detail soap);
}
