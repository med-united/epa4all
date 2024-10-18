package de.servicehealth.epa4all.server.cetp.decoder;

import de.health.service.cetp.codec.CETPEventDecoderFactory;
import de.health.service.cetp.domain.eventservice.event.mapper.CetpEventMapper;
import de.servicehealth.config.api.IUserConfigurations;
import io.netty.channel.ChannelInboundHandler;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CETPDecoderFactory implements CETPEventDecoderFactory {

    @Override
    public ChannelInboundHandler build(IUserConfigurations configurations, CetpEventMapper eventMapper) {
        return new CETPDecoder(configurations, eventMapper);
    }
}
