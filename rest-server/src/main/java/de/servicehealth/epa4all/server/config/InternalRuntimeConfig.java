package de.servicehealth.epa4all.server.config;

import de.health.service.config.api.IRuntimeConfig;

public class InternalRuntimeConfig implements IRuntimeConfig {

    private String idpClientId;
    private String idpAuthRequestRedirectURL;
    private String ehbaHandle;
    private String smcbHandle;
    private String iccsn;

    public InternalRuntimeConfig(String idpClientId, String idpAuthRequestRedirectURL) {
        this.idpClientId = idpClientId;
        this.idpAuthRequestRedirectURL = idpAuthRequestRedirectURL;
    }

    public InternalRuntimeConfig(String iccsn) {
        this.iccsn = iccsn;
    }

    @Override
    public String getEHBAHandle() {
        return ehbaHandle;
    }

    @Override
    public String getSMCBHandle() {
        return smcbHandle;
    }

    @Override
    public String getIccsn() {
        return iccsn;
    }

    @Override
    public void setIccsn(String iccsn) {
        this.iccsn = iccsn;
    }

    @Override
    public void setEHBAHandle(String ehbaHandle) {
        this.ehbaHandle = ehbaHandle;
    }

    @Override
    public void setSMCBHandle(String smcbHandle) {
        this.smcbHandle = smcbHandle;
    }

    @Override
    public boolean isSendPreview() {
        return false;
    }

    @Override
    public String getIdpAuthRequestRedirectURL() {
        return idpAuthRequestRedirectURL;
    }

    @Override
    public String getIdpClientId() {
        return idpClientId;
    }
}
