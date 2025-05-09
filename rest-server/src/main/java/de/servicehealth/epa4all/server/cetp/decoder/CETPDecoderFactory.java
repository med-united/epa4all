package de.servicehealth.epa4all.server.cetp.decoder;

import de.gematik.ws.conn.eventservice.v7.Event;
import de.health.service.cetp.codec.CETPEventDecoderFactory;
import de.health.service.cetp.domain.eventservice.event.mapper.CetpEventMapper;
import de.health.service.config.api.IUserConfigurations;
import io.netty.channel.ChannelInboundHandler;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CETPDecoderFactory implements CETPEventDecoderFactory {

    private final CetpEventMapper<Event> eventMapper;

    public CETPDecoderFactory(CetpEventMapper<Event> eventMapper) {
        this.eventMapper = eventMapper;
    }

    @Override
    public ChannelInboundHandler build(IUserConfigurations configurations) {
        return new CETPDecoder(configurations, eventMapper);
    }
}
