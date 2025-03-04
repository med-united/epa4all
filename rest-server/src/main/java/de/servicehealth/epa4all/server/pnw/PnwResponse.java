package de.servicehealth.epa4all.server.pnw;

import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.xml.bind.annotation.XmlAccessType.FIELD;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@XmlRootElement(name = "entitlement")
@XmlAccessorType(FIELD)
public class PnwResponse {

    String kvnr;
    String startDate;
    String endDate;
    String street;
    String text;
}