package de.servicehealth.vau;

import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public record VauResponse(int status, String generalError, byte[] payload, List<Pair<String, String>> headers) {
}
