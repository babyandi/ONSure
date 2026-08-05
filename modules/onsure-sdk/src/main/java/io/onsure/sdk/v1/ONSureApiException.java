package io.onsure.sdk.v1;

import java.io.IOException;

/** Structured non-success response from the ONSure Local API. */
public final class ONSureApiException extends IOException {
    private final int statusCode;
    private final String code;
    private final String requestId;
    private final boolean retryable;

    public ONSureApiException(int statusCode, String code, String message, String requestId, boolean retryable) {
        super(normalizeMessage(code, message));
        if (statusCode < 400 || statusCode > 599) throw new IllegalArgumentException("statusCode");
        this.statusCode = statusCode;
        this.code = code == null || code.isBlank() ? "LOCAL_API_ERROR" : code;
        this.requestId = requestId == null ? "NOT_AVAILABLE" : requestId;
        this.retryable = retryable;
    }

    public int statusCode() { return statusCode; }
    public String code() { return code; }
    public String requestId() { return requestId; }
    public boolean retryable() { return retryable; }

    private static String normalizeMessage(String code, String message) {
        if (message != null && !message.isBlank()) return message;
        return code == null || code.isBlank() ? "LOCAL_API_ERROR" : code;
    }
}
