package de.servicehealth.epa4all.serviceport;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;

public interface IServicePortAggregator {

    ContextType getContextType();

    CardServicePortType getCardService();

    EventServicePortType getEventService();

    CertificateServicePortType getCertificateService();

    AuthSignatureServicePortType getAuthSignatureService();
}
