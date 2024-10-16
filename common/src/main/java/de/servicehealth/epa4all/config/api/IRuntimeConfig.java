package de.servicehealth.epa4all.config.api;

@SuppressWarnings("unused")
public interface IRuntimeConfig {

    String getEHBAHandle();

    String getSMCBHandle();

    void setEHBAHandle(String eHBAHandle);

    void setSMCBHandle(String smcbHandle);

    boolean isSendPreview();

    String getIdpAuthRequestRedirectURL();

    String getIdpClientId();
}
