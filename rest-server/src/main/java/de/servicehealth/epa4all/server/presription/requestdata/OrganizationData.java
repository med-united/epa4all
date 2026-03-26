package de.servicehealth.epa4all.server.presription.requestdata;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class OrganizationData {

    @ApiModelProperty(value = "Telematik-ID of the requesting organization", example = "5-2.58.00000042")
    String telematikId;

    @ApiModelProperty(value = "Organization name", example = "Pflegeheim Immergrün")
    String orgName;

    @ApiModelProperty(value = "Organization type OID code", example = "1.2.276.0.76.4.245")
    String orgTypeCode;

    public OrganizationData(String telematikId, String orgName, String orgTypeCode) {
        this.telematikId = telematikId;
        this.orgName = orgName;
        this.orgTypeCode = orgTypeCode;
    }
}