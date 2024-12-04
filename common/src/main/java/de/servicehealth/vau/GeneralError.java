package de.servicehealth.vau;

public record GeneralError(String MessageType, int ErrorCode, String ErrorMessage) {
}
