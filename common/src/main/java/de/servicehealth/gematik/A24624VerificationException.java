package de.servicehealth.gematik;

import java.io.Serial;

/**
 * Thrown by {@link A24624Verifier} when one of the A_24624-01 checks on the
 * server-side {@code SignedPublicVauKeys} fails.
 * <p>
 * Unchecked so it propagates cleanly through lib-vau's {@code @SneakyThrows} boundary
 * (the VAU client state machine does not declare checked exceptions on the public API).
 */
public class A24624VerificationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public A24624VerificationException(String message) {
        super(message);
    }

    public A24624VerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
