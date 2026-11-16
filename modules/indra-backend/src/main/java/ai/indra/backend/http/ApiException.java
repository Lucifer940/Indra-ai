package ai.indra.backend.http;

/** Exception carrying an HTTP status and a structured error code. */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final String field;

    public ApiException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(int status, String code, String message, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, ai.indra.core.model.ApiError.BAD_REQUEST, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, ai.indra.core.model.ApiError.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, ai.indra.core.model.ApiError.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, ai.indra.core.model.ApiError.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, ai.indra.core.model.ApiError.CONFLICT, message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(429, ai.indra.core.model.ApiError.RATE_LIMITED, message);
    }

    public static ApiException unavailable(String message) {
        return new ApiException(503, "service_unavailable", message);
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }
}
