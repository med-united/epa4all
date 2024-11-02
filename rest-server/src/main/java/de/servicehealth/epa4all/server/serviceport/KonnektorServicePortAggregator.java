package de.servicehealth.epa4all.server.serviceport;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDServicePortType;

public class KonnektorServicePortAggregator implements IKonnektorServicePortsAPI {

    private final ContextType contextType;
    private final CardServicePortType cardService;
    private final EventServicePortType eventService;
    private final VSDServicePortType vsdServicePortType;
    private final CertificateServicePortType certificateService;
    private final AuthSignatureServicePortType authSignatureService;

    public KonnektorServicePortAggregator(
        ContextType contextType,
        CardServicePortType cardService,
        EventServicePortType eventService,
        VSDServicePortType vsdServicePortType,
        CertificateServicePortType certificateService,
        AuthSignatureServicePortType authSignatureService
    ) {
        this.contextType = contextType;
        this.cardService = cardService;
        this.eventService = eventService;
        this.vsdServicePortType = vsdServicePortType;
        this.certificateService = certificateService;
        this.authSignatureService = authSignatureService;
    }

    @Override
    public ContextType getContextType() {
        return contextType;
    }

    @Override
    public CardServicePortType getCardService() {
        return cardService;
    }

    @Override
    public EventServicePortType getEventService() {
        return eventService;
    }

    @Override
    public VSDServicePortType getVSDServicePortType() {
        return vsdServicePortType;
    }

    @Override
    public CertificateServicePortType getCertificateService() {
        return certificateService;
    }

    @Override
    public AuthSignatureServicePortType getAuthSignatureService() {
        return authSignatureService;
    }
}
