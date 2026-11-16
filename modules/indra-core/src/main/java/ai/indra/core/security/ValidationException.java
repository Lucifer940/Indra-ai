package ai.indra.core.security;

/** Validation failure tied to a specific request field. */
public class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
