package de.servicehealth.epa4all.server.presription.requestdata;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class PractitionerData {

    @ApiModelProperty(value = "Destination practitioner/practice name", example = "Praxis Dr. Müller")
    String name;

    @ApiModelProperty(value = "KIM address of the destination practitioner", example = "mailto:dr.mueller@praxis.kim.telematik")
    String kimAddress;

    public PractitionerData(String name, String kimAddress) {
        this.name = name;
        this.kimAddress = kimAddress;
    }
}