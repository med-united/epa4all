package de.servicehealth.epa4all;

import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public record VauResponse(int status, byte[] payload, List<Pair<String, String>> headers) {
}
