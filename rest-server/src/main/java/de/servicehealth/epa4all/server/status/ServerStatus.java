package de.servicehealth.epa4all.server.status;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

@Getter
public class ServerStatus implements Serializable {

    @Serial
    private static final long serialVersionUID = -6451145592072788128L;

    private volatile String connectorInformation;
    private volatile String idpInformation;
    private volatile String smcbInformation;
    private volatile String cautInformation;
    private volatile String ehbaInformation;

    private volatile boolean connectorReachable;
    private volatile boolean idpReachable;
    private volatile boolean smcbAvailable;
    private volatile boolean cautReadable;
    private volatile boolean ehbaAvailable;

    public void setConnectorReachable(boolean isOK, String statusDescription) {
        this.connectorReachable = isOK;
        this.connectorInformation = statusDescription;
    }

    public void setIdpReachable(boolean isOK, String statusDescription) {
        this.idpReachable = isOK;
        this.idpInformation = statusDescription;
    }

    public void setSmcbAvailable(boolean isOK, String statusDescription) {
        this.smcbAvailable = isOK;
        this.smcbInformation = statusDescription;
    }

    public void setCautReadable(boolean isOK, String statusDescription) {
        this.cautReadable = isOK;
        this.cautInformation = statusDescription;
    }

    public void setEhbaAvailable(boolean isOK, String statusDescription) {
        this.ehbaAvailable = isOK;
        this.ehbaInformation = statusDescription;
    }
}