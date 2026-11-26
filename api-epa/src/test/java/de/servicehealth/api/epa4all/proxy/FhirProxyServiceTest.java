package de.servicehealth.api.epa4all.proxy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.servicehealth.api.epa4all.proxy.FhirProxyService.buildContentHeaders;
import static de.servicehealth.api.epa4all.proxy.FhirProxyService.isXmlRequested;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void callerContentTypeIsForwarded() {
        byte[] body = new byte[]{1, 2, 3};
        assertEquals(
            List.of(Pair.of("Content-Type", "application/fhir+xml"), Pair.of("Content-Length", "3")),
            buildContentHeaders(body, "application/fhir+xml")
        );
        assertEquals(
            List.of(Pair.of("Content-Type", "application/fhir+json; charset=utf-8"), Pair.of("Content-Length", "3")),
            buildContentHeaders(body, "application/fhir+json; charset=utf-8")
        );
    }

    @Test
    void missingContentTypeFallsBackToFhirJson() {
        assertEquals(
            List.of(Pair.of("Content-Type", "application/fhir+json"), Pair.of("Content-Length", "1")),
            buildContentHeaders(new byte[]{1}, null)
        );
    }

    @Test
    void emptyBodyHasNoContentHeaders() {
        assertEquals(List.of(), buildContentHeaders(null, "application/fhir+xml"));
        assertEquals(List.of(), buildContentHeaders(new byte[0], "application/fhir+xml"));
    }
}
