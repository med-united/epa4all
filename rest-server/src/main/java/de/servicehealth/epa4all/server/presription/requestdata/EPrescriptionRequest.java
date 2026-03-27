package de.servicehealth.epa4all.server.presription.requestdata;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "Prescription request based on a medication selected from the ePA Medication List")
public class EPrescriptionRequest {

    @ApiModelProperty(
        value = "Base64-encoded ePA MedicationRequest JSON (contains dosage, quantity, dispenseRequest)", required = true
    )
    String epaMedicationRequestBase64;

    @ApiModelProperty(
        value = "Base64-encoded ePA Medication JSON (contains PZN, name, form, amount)", required = true
    )
    String epaMedicationBase64;

    @ApiModelProperty(
        value = "Practitioner display name for LDAP/VZD lookup of KIM address",
        required = true,
        example = "Tanja Freifrau Dåvid"
    )
    String practitionerName;

    PatientData patientData;

    OrganizationData organizationData;
}
