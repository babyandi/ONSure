package io.onsure.provider.spi;

/** Classified provider failure safe for policy handling; secret values must not be included. */
public final class ProviderException extends Exception {
    private final String code;
    private final boolean retryable;

    public ProviderException(String code, String message, boolean retryable) {
        super(message);
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{1,79}")) throw new IllegalArgumentException("code");
        this.code = code;
        this.retryable = retryable;
    }

    public String code() { return code; }
    public boolean retryable() { return retryable; }
}
