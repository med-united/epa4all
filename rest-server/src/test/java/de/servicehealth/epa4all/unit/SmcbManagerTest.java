package de.servicehealth.epa4all.unit;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static de.servicehealth.epa4all.server.idp.IdpClient.BOUNCY_CASTLE_PROVIDER;
import static de.servicehealth.utils.SSLUtils.extractTelematikIdFromCertificate;
import static de.servicehealth.utils.ServerUtils.makePrefixPath;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SmcbManagerTest {

	@Test
	void testExtractTelematikIdFromCertificate() {
		try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509", BOUNCY_CASTLE_PROVIDER);
            InputStream inputStream = getClass().getResourceAsStream(makePrefixPath("certs", "SMC-B", "Bad_ApothekeTESTONLY-80276883110000116352-aut-rsa.cer"));
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(inputStream);
            assertEquals("3-SMC-B-Testkarte-883110000116352", extractTelematikIdFromCertificate(cert));
		} catch (CertificateException e) {
            throw new RuntimeException(e);
        }
	}
}
