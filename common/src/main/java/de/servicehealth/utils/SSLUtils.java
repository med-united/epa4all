package de.servicehealth.utils;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers;
import org.bouncycastle.asn1.isismtt.x509.AdmissionSyntax;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Base64;

import static de.servicehealth.utils.SSLUtils.KeyStoreType.PKCS12;
import static de.servicehealth.utils.SSLUtils.SslContextType.TLS;
import static javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm;

public class SSLUtils {

    public enum SslContextType {
        SSL, TLS
    }

    public enum KeyStoreType {
        JKS, PKCS12
    }

    public static SSLContext createSSLContext(
        String certificate,
        String password,
        X509TrustManager trustManager,
        SSLContext defaultSSLContext) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(getClientCertificateBytes(certificate))) {
            SSLContextBundle sslContextBundle = createSSLContextBundle(inputStream, password, trustManager, PKCS12);
            return sslContextBundle.getSslContext();
        } catch (Exception e) {
            return defaultSSLContext;
        }
    }

    public static SSLContextBundle createSSLContextBundle(
        InputStream inputStream,
        String password,
        X509TrustManager trustManager,
        KeyStoreType ksType
    ) throws Exception {
        SSLContext sslContext = SSLContext.getInstance(TLS.name());

        KeyStore keyStore = KeyStore.getInstance(ksType.name());
        keyStore.load(inputStream, password.toCharArray());
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password.toCharArray());
        TrustManager[] trustManagers = trustManager == null ? null : new TrustManager[] { trustManager };
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagers, null);

        return new SSLContextBundle(sslContext, keyManagerFactory);
    }

    public static byte[] getClientCertificateBytes(String base64UrlCertificate) {
        String clientCertificateString = base64UrlCertificate.split(",")[1];
        return Base64.getDecoder().decode(clientCertificateString);
    }

    public static String extractTelematikIdFromCertificate(X509Certificate cert) {
        // https://oidref.com/1.3.36.8.3.3
        byte[] admission = cert.getExtensionValue(ISISMTTObjectIdentifiers.id_isismtt_at_admission.toString());
        try (ASN1InputStream input = new ASN1InputStream(admission)) {
            ASN1Primitive p = input.readObject();
            if (p != null) {
                // Based on https://stackoverflow.com/a/20439748
                DEROctetString derOctetString = (DEROctetString) p;
                ASN1InputStream asnInputStream = new ASN1InputStream(new ByteArrayInputStream(derOctetString.getOctets()));
                ASN1Primitive asn1 = asnInputStream.readObject();
                AdmissionSyntax admissionSyntax = AdmissionSyntax.getInstance(asn1);
                return admissionSyntax.getContentsOfAdmissions()[0].getProfessionInfos()[0].getRegistrationNumber();
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}
