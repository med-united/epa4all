package de.servicehealth.epa4all.xds;

import lombok.Getter;

import java.util.Map;

public class CustomCodingScheme {

    public static final String NODE_REPRESENTATION = "nodeRepresentation";
    public static final String NAME = "name";

    @Getter
    private final CodingScheme codingScheme;
    private final Map<String, String> elementsMap;

    public CustomCodingScheme(CodingScheme codingScheme, Map<String, String> elementsMap) {
        this.codingScheme = codingScheme;
        this.elementsMap = elementsMap;
    }

    public String getNodeRepresentationOrDefault(String defaultValue) {
        return elementsMap.getOrDefault(NODE_REPRESENTATION, defaultValue);
    }

    public String getNameOrDefault(String defaultValue) {
        return elementsMap.getOrDefault(NAME, defaultValue);
    }
}