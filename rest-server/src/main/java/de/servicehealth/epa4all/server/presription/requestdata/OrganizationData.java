package de.servicehealth.epa4all.server.presription.requestdata;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrganizationData {

    @ApiModelProperty(value = "Telematik-ID of the requesting organization", example = "1-SMC-B-Testkarte--883110000162363")
    String telematikId;

    @ApiModelProperty(value = "Organization name", example = "Praxis Xenia Gräfin d'Aubertinó")
    String orgName;

    @ApiModelProperty(value = "Organization type OID code", example = "1.2.276.0.76.4.50")
    String orgTypeCode;
}