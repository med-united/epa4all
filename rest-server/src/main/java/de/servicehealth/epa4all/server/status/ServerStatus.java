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
    private volatile String idpaccesstokenInformation;
    private volatile String smcbInformation;
    private volatile String cautInformation;
    private volatile String ehbaInformation;
    private volatile String comfortSignatureInformation;
    private volatile String fachdienstInformation;

    private volatile boolean connectorReachable;
    private volatile boolean idpReachable;
    private volatile boolean idpaccesstokenObtainable;
    private volatile boolean smcbAvailable;
    private volatile boolean cautReadable;
    private volatile boolean ehbaAvailable;
    private volatile boolean comfortSignatureAvailable;
    private volatile boolean fachdienstReachable;

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

    public void setComfortsignatureAvailable(boolean isOK, String statusDescription) {
        this.comfortSignatureAvailable = isOK;
        this.comfortSignatureInformation = statusDescription;
    }

    public void setIdpaccesstokenObtainable(boolean isOK, String statusDescription) {
        this.idpaccesstokenObtainable = isOK;
        this.idpaccesstokenInformation = statusDescription;
    }

    public void setFachdienstReachable(boolean isOK, String statusDescription) {
        this.fachdienstReachable = isOK;
        this.fachdienstInformation = statusDescription;
    }
}