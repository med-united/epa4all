package de.servicehealth.epa4all;

public record GeneralError(String MessageType, int ErrorCode, String Message) {
}
