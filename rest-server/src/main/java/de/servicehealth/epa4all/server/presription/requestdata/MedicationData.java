package de.servicehealth.epa4all.server.presription.requestdata;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class MedicationData {

    @ApiModelProperty(value = "PZN drug code", example = "00027950")
    String pzn;

    @ApiModelProperty(value = "Medication display name", example = "Ibuprofen Atid 600mg 10 ST")
    String name;

    @ApiModelProperty(value = "Dosage form code (KBV Darreichungsform)", example = "FTA")
    String formCode;

    @ApiModelProperty(value = "Package size (Normgröße)", example = "N1")
    String normgroesse;

    @ApiModelProperty(value = "Dosage instruction text", example = "1-0-1-0")
    String dosage;

    @ApiModelProperty(value = "Number of packages", example = "1")
    int quantity;

    @ApiModelProperty(value = "Quantity unit code", example = "{Package}")
    String quantityUnit;

    public MedicationData(
        String pzn,
        String name,
        String formCode,
        String normgroesse,
        String dosage,
        int quantity,
        String quantityUnit
    ) {
        this.pzn = pzn;
        this.name = name;
        this.formCode = formCode;
        this.normgroesse = normgroesse;
        this.dosage = dosage;
        this.quantity = quantity;
        this.quantityUnit = quantityUnit;
    }
}
