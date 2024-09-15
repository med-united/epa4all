package de.servicehealth.epa4all.idp;

import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


public class FakeTrustManager implements X509TrustManager {

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
        // Trust all client certificates
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
        // Trust all server certificates
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        // Return an empty array of accepted issuers
        return new X509Certificate[0];
    }

    public static TrustManager[] getTrustManagers() {
        return new TrustManager[]{new FakeTrustManager()};
    }
}