package de.servicehealth.epa4all.idp;

import javax.inject.Inject;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.FaultMessage;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.signatureservice.v7.BinaryDocumentType;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticate;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticateResponse;
import de.servicehealth.api.AuthorizationSmcBApi;
import jakarta.xml.bind.DatatypeConverter;
import oasis.names.tc.dss._1_0.core.schema.Base64Data;

public class IdpClient {

    String userAgent = "ServiceHealth/1.0";

    @Inject
    AuthorizationSmcBApi authorizationService;

    @Inject
    AuthSignatureServicePortType authSignatureServicePortType;

    @Inject
    CertificateServicePortType certificateServicePortType;

    @Inject
    ContextType contextType;

    @Inject
    String smcbHandle;

    public String getBearerToken() {
        // A_24881 - Nonce anfordern für Erstellung "Attestation der Umgebung"
        String nonce = authorizationService.getNonce(userAgent).getNonce();

        // A_24882-01 - Signatur clientAttest
        byte[] signature = signClientAttestation(nonce);

        // A_20666-02 - Auslesen des Authentisierungszertifikates 
        // TODO
        try {
            certificateServicePortType.readCardCertificate(null);
        } catch (de.gematik.ws.conn.certificateservice.wsdl.v6_0.FaultMessage e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // A_24760 - Start der Nutzerauthentifizierung
        authorizationService.sendAuthorizationRequestSC(userAgent);

        

        return "";
    }

    public byte[] signClientAttestation(String nonce) {

        
        ExternalAuthenticate externalAuthenticate = new ExternalAuthenticate();
        BinaryDocumentType binaryDocumentType = new BinaryDocumentType();
        Base64Data base64Data = new Base64Data();
        base64Data.setValue(DatatypeConverter.parseBase64Binary(nonce));
        binaryDocumentType.setBase64Data(base64Data);
        externalAuthenticate.setBinaryString(binaryDocumentType);
        externalAuthenticate.setContext(contextType);
        externalAuthenticate.setCardHandle(smcbHandle);
        ExternalAuthenticate.OptionalInputs optionalInputs = new ExternalAuthenticate.OptionalInputs();
        // A_24883-02 - clientAttest als ECDSA-Signatur
        optionalInputs.setSignatureType("urn:bsi:tr:03111:ecdsa");
        externalAuthenticate.setOptionalInputs(optionalInputs);
        ExternalAuthenticateResponse externalAuthenticateResponse = null;
        try {
            externalAuthenticateResponse = authSignatureServicePortType.externalAuthenticate(externalAuthenticate);
            // TODO: Convert ECDSA ASN.1 to JWT Concanted
        } catch (FaultMessage e) {
            e.printStackTrace();
        }

        // TODO: A_24884-01 - clientAttest signieren als PKCS#1-Signatur



        return externalAuthenticateResponse.getSignatureObject().getBase64Signature().getValue();

    }
}
