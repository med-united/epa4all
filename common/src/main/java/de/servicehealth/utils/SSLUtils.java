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
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

import static de.servicehealth.utils.SSLUtils.KeyStoreType.PKCS12;
import static de.servicehealth.utils.SSLUtils.SslContextType.TLS;

public class SSLUtils {

    public enum SslContextType {
        SSL, TLS
    }

    public enum KeyStoreType {
        JKS, PKCS12
    }

    public static SSLContext createSSLContext(String certificate, String password, SSLContext defaultSSLContext) {
        byte[] clientCertificateBytes = getClientCertificateBytes(certificate);
        try (ByteArrayInputStream certInputStream = new ByteArrayInputStream(clientCertificateBytes)) {
            SSLResult sslResult = initSSLContext(certInputStream, password);
            return sslResult.getSslContext();
        } catch (Exception e) {
            return defaultSSLContext;
        }
    }

    public static SSLResult initSSLContext(InputStream certInputStream, String certPass) throws Exception {
        SSLContext sslContext = SSLContext.getInstance(TLS.name());

        KeyStore ks = KeyStore.getInstance(PKCS12.name());
        ks.load(certInputStream, certPass.toCharArray());
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(ks, certPass.toCharArray());
        sslContext.init(keyManagerFactory.getKeyManagers(), getFakeTrustManagers(), null);

        System.setProperty("javax.xml.accessExternalDTD", "all");
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

        return new SSLResult(sslContext, keyManagerFactory);
    }

    public static byte[] getClientCertificateBytes(String base64UrlCertificate) {
        String clientCertificateString = base64UrlCertificate.split(",")[1];
        return Base64.getDecoder().decode(clientCertificateString);
    }

    public static SSLContext createFakeSSLContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance(TLS.name());
        sslContext.init(null, getFakeTrustManagers(), new SecureRandom());
        return sslContext;
    }

    public static TrustManager[] getFakeTrustManagers() {
        return new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
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
