package de.servicehealth.utils;

import de.servicehealth.config.api.IUserConfigurations;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
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

    public static SSLContext createSSLContext(IUserConfigurations userConfigurations, SSLContext defaultSSLContext) {
        byte[] clientCertificateBytes = getClientCertificateBytes(userConfigurations);
        try (ByteArrayInputStream certInputStream = new ByteArrayInputStream(clientCertificateBytes)) {
            SSLResult sslResult = initSSLContext(certInputStream, userConfigurations.getClientCertificatePassword());
            return sslResult.getSslContext();
        } catch (Exception e) {
            return defaultSSLContext;
        }
    }

    public static SSLContext createSSLContext(InputStream certInputStream, String certPass) throws Exception {
        SSLResult sslResult = initSSLContext(certInputStream, certPass);
        return sslResult.getSslContext();
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

    public static byte[] getClientCertificateBytes(IUserConfigurations userConfigurations) {
        String base64UrlCertificate = userConfigurations.getClientCertificate();
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
}
