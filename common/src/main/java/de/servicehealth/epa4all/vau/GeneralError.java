package de.servicehealth.epa4all.vau;

public record GeneralError(String MessageType, int ErrorCode, String Message) {
}
