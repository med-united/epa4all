package de.servicehealth.epa4all.server.smcb;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static de.servicehealth.epa4all.server.idp.IdpClient.BOUNCY_CASTLE_PROVIDER;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SmcbManagerTest {

    @Inject
    WebdavSmcbManager webdavSmcbManager;

	@Test
	void testExtractTelematikIdFromCertificate() {
		try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509", BOUNCY_CASTLE_PROVIDER);
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(getClass().getResourceAsStream("/certs/SMC-B/Bad_ApothekeTESTONLY-80276883110000116352-aut-rsa.cer"));
            assertEquals("3-SMC-B-Testkarte-883110000116352", webdavSmcbManager.extractTelematikIdFromCertificate(cert));
		} catch (CertificateException e) {
            throw new RuntimeException(e);
        }
	}
}
