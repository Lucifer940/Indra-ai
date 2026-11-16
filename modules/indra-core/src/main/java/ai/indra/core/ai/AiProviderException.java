package ai.indra.core.ai;

/** Typed provider failure so the UI can explain exactly what went wrong. */
public class AiProviderException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Kind {
        UNSUPPORTED,
        NOT_CONFIGURED,
        AUTH_FAILED,
        RATE_LIMITED,
        NETWORK,
        TIMEOUT,
        BAD_RESPONSE,
        SERVER_ERROR
    }

    private final Kind kind;
    private final int statusCode;

    public AiProviderException(Kind kind, String message) {
        this(kind, message, 0, null);
    }

    public AiProviderException(Kind kind, String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.statusCode = statusCode;
    }

    public Kind getKind() {
        return kind;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /** True when another key or provider could plausibly succeed. */
    public boolean isRetryableElsewhere() {
        return kind == Kind.RATE_LIMITED || kind == Kind.SERVER_ERROR || kind == Kind.TIMEOUT
                || kind == Kind.NETWORK || kind == Kind.AUTH_FAILED;
    }
}
