package de.servicehealth.epa4all.server.config;

import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@XmlRootElement(name = "KonnektorConfig")
public class KonnektorUserConfig {

    @XmlElement(name = "clientCertificate")
    @JsonbProperty("clientCertificate")
    String clientCertificate;

    @XmlElement(name = "clientSystemId")
    @JsonbProperty("clientSystemId")
    String clientSystemId;

    @XmlElement(name = "connectorBaseURL")
    @JsonbProperty("connectorBaseURL")
    String connectorBaseURL;

    @XmlElement(name = "mandantId")
    @JsonbProperty("mandantId")
    String mandantId;

    @XmlElement(name = "userId")
    @JsonbProperty("userId")
    String userId;

    @XmlElement(name = "version")
    @JsonbProperty("version")
    String version;

    @XmlElement(name = "workplaceId")
    @JsonbProperty("workplaceId")
    String workplaceId;

    @XmlElement(name = "cardlinkServerURL")
    @JsonbProperty("cardlinkServerURL")
    String cardlinkServerURL;
}
