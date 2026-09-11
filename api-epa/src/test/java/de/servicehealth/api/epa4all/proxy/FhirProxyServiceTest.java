package de.servicehealth.api.epa4all.proxy;

import org.junit.jupiter.api.Test;

import static de.servicehealth.api.epa4all.proxy.FhirProxyService.isXmlRequested;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FhirProxyServiceTest {

    @Test
    void xmlRequestedViaAcceptHeader() {
        assertTrue(isXmlRequested("application/fhir+xml", null));
        assertTrue(isXmlRequested("application/fhir+xml;q=1.0, application/fhir+json;q=0.9", null));
    }

    @Test
    void jsonByDefault() {
        assertFalse(isXmlRequested(null, null));
        assertFalse(isXmlRequested("application/fhir+json", null));
        assertFalse(isXmlRequested("*/*", "_count=10"));
    }

    @Test
    void browserXhtmlAcceptDoesNotTriggerXml() {
        assertFalse(isXmlRequested("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", null));
    }

    @Test
    void xmlRequestedViaFormatParam() {
        assertTrue(isXmlRequested(null, "_format=application/fhir+xml"));
        assertTrue(isXmlRequested("application/fhir+json", "_count=10&_format=application/fhir+xml"));
    }

    @Test
    void formatParamOverridesAcceptHeader() {
        assertFalse(isXmlRequested("application/fhir+xml", "_format=json"));
        assertTrue(isXmlRequested("application/fhir+json", "_format=application/fhir+xml"));
    }
}
