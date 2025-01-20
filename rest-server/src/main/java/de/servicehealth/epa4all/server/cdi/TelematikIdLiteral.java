package de.servicehealth.epa4all.server.cdi;

import jakarta.enterprise.util.AnnotationLiteral;

import java.io.Serial;

@SuppressWarnings("ClassExplicitlyAnnotation")
public class TelematikIdLiteral extends AnnotationLiteral<TelematikId> implements TelematikId {
    @Serial
    private static final long serialVersionUID = 6754410804827384121L;
}
