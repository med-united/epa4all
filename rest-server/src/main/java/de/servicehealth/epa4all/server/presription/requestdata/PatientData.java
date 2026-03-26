package de.servicehealth.epa4all.server.presription.requestdata;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class PatientData {

    @ApiModelProperty(value = "Patient KVNR (health insurance number)", example = "X234567890")
    String kvnr;

    @ApiModelProperty(value = "Patient family name", example = "Königsstein")
    String family;

    @ApiModelProperty(value = "Patient given name", example = "Ludger")
    String given;

    @ApiModelProperty(value = "Patient birth date (yyyy-MM-dd)", example = "1935-06-22")
    String birthDate;

    @ApiModelProperty(value = "Street name", example = "Musterstr.")
    String street;

    @ApiModelProperty(value = "House number", example = "1")
    String houseNumber;

    @ApiModelProperty(value = "City", example = "Berlin")
    String city;

    @ApiModelProperty(value = "Postal code", example = "10623")
    String postalCode;

    public PatientData(String kvnr, String family, String given, String birthDate, String street, String houseNumber, String city, String postalCode) {
        this.kvnr = kvnr;
        this.family = family;
        this.given = given;
        this.birthDate = birthDate;
        this.street = street;
        this.houseNumber = houseNumber;
        this.city = city;
        this.postalCode = postalCode;
    }
}
