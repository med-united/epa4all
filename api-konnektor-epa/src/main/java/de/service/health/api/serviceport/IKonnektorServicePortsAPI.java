package de.service.health.api.serviceport;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDServicePortType;

public interface IKonnektorServicePortsAPI {

    ContextType getContextType();

    CardServicePortType getCardService();

    EventServicePortType getEventService();

    VSDServicePortType getVSDServicePortType();

    CertificateServicePortType getCertificateService();

    AuthSignatureServicePortType getAuthSignatureService();
}
