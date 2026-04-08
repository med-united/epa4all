package de.servicehealth.epa4all.xds;

import lombok.Getter;

@Getter
public enum CodingScheme {

    PracticeSettingClassification("1.3.6.1.4.1.19376.3.276.1.5.4"),
    FacilityTypeCodeClassification("1.3.6.1.4.1.19376.3.276.1.5.2"),
    ClassCodeClassification("1.3.6.1.4.1.19376.3.276.1.5.8"),
    TypeCodeClassification("1.3.6.1.4.1.19376.3.276.1.5.9"),
    FormatCodeClassification("1.3.6.1.4.1.19376.3.276.1.5.6"),
    FormatCodeClassificationPfd("1.3.6.1.4.1.19376.1.2.3"),
    EventCodeListClassification("1.2.276.0.76.5.223");

    private final String code;

    CodingScheme(String code) {
        this.code = code;
    }
}
