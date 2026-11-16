package ai.indra.core.model;

import ai.indra.core.json.JsonObject;

/** Structured error body returned by every INDRA AI endpoint. */
public class ApiError {

    public static final String BAD_REQUEST = "bad_request";
    public static final String VALIDATION_FAILED = "validation_failed";
    public static final String UNAUTHORIZED = "unauthorized";
    public static final String FORBIDDEN = "forbidden";
    public static final String NOT_FOUND = "not_found";
    public static final String CONFLICT = "conflict";
    public static final String RATE_LIMITED = "rate_limited";
    public static final String PROVIDER_ERROR = "provider_error";
    public static final String OFFLINE = "offline";
    public static final String INTERNAL = "internal_error";

    private final String code;
    private final String message;
    private final String field;
    private final String requestId;
    private final int status;

    public ApiError(int status, String code, String message, String field, String requestId) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.field = field;
        this.requestId = requestId;
    }

    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, null, null);
    }

    public static ApiError badRequest(String message) {
        return new ApiError(400, BAD_REQUEST, message, null, null);
    }

    public static ApiError unauthorized(String message) {
        return new ApiError(401, UNAUTHORIZED, message, null, null);
    }

    public static ApiError forbidden(String message) {
        return new ApiError(403, FORBIDDEN, message, null, null);
    }

    public static ApiError notFound(String message) {
        return new ApiError(404, NOT_FOUND, message, null, null);
    }

    public static ApiError conflict(String message) {
        return new ApiError(409, CONFLICT, message, null, null);
    }

    public static ApiError rateLimited(String message) {
        return new ApiError(429, RATE_LIMITED, message, null, null);
    }

    public static ApiError internal(String message) {
        return new ApiError(500, INTERNAL, message, null, null);
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getField() {
        return field;
    }

    public String getRequestId() {
        return requestId;
    }

    public ApiError withRequestId(String id) {
        return new ApiError(status, code, message, field, id);
    }

    public JsonObject toJson() {
        JsonObject error = new JsonObject();
        error.put("code", code);
        error.put("message", message);
        if (field != null) {
            error.put("field", field);
        }
        if (requestId != null) {
            error.put("requestId", requestId);
        }
        JsonObject root = new JsonObject();
        root.put("ok", Boolean.FALSE);
        root.put("error", error);
        return root;
    }
}
