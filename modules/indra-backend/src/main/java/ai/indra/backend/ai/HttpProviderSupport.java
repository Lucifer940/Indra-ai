package ai.indra.backend.ai;

import ai.indra.core.ai.AiProviderException;
import ai.indra.core.net.Http;

import java.io.IOException;
import java.io.InputStream;

/**
 * Shared HTTP plumbing for provider clients.
 *
 * <p>Uses {@link Http} (HttpURLConnection) instead of {@code java.net.http} so the
 * same provider code runs on a Windows PC and inside the Android app.</p>
 */
final class HttpProviderSupport {

    private HttpProviderSupport() {
    }

    /** Buffered JSON answer. */
    static final class JsonResponse {
        final int status;
        final String body;

        JsonResponse(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }

        int statusCode() {
            return status;
        }

        String body() {
            return body;
        }
    }

    /** Open streamed answer (Server-Sent Events). */
    static final class StreamResponse {
        final int status;
        final InputStream stream;

        StreamResponse(int status, InputStream stream) {
            this.status = status;
            this.stream = stream;
        }

        int status() {
            return status;
        }

        InputStream body() {
            return stream;
        }

        void close() {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException ignored) {
                // already closed
            }
        }
    }

    static JsonResponse postJson(String url, String body, String... headers) throws AiProviderException {
        try {
            Http.Response response = Http.postJson(url, body, Http.DEFAULT_READ_TIMEOUT, headers);
            try {
                return new JsonResponse(response.statusCode(), response.body());
            } finally {
                response.close();
            }
        } catch (IOException error) {
            throw networkError(error);
        }
    }

    static StreamResponse postStream(String url, String body, String... headers) throws AiProviderException {
        try {
            Http.Response response = Http.postJson(url, body, Http.DEFAULT_READ_TIMEOUT, headers);
            return new StreamResponse(response.statusCode(), response.stream());
        } catch (IOException error) {
            throw networkError(error);
        }
    }

    static void checkStatus(JsonResponse response, String providerLabel) throws AiProviderException {
        checkStatus(response.status, response.body, providerLabel);
    }

    static void checkStatus(int status, String body, String providerLabel) throws AiProviderException {
        if (status >= 200 && status < 300) {
            return;
        }
        String detail = body == null ? "" : body;
        if (detail.length() > 400) {
            detail = detail.substring(0, 400);
        }
        switch (status) {
            case 401:
            case 403:
                throw new AiProviderException(AiProviderException.Kind.AUTH_FAILED,
                        providerLabel + " rejected the API key. Check the key in Settings.", status, null);
            case 429:
                throw new AiProviderException(AiProviderException.Kind.RATE_LIMITED,
                        providerLabel + " rate limit reached. Trying another key.", status, null);
            default:
                if (status >= 500) {
                    throw new AiProviderException(AiProviderException.Kind.SERVER_ERROR,
                            providerLabel + " is having trouble (" + status + "). " + detail, status, null);
                }
                throw new AiProviderException(AiProviderException.Kind.BAD_RESPONSE,
                        providerLabel + " error (" + status + "): " + detail, status, null);
        }
    }

    static String joinUrl(String base, String path) {
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + (path.startsWith("/") ? path : "/" + path);
    }

    private static AiProviderException networkError(IOException error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        boolean timeout = message.toLowerCase(java.util.Locale.ROOT).contains("timed out");
        return timeout
                ? new AiProviderException(AiProviderException.Kind.TIMEOUT,
                        "The AI provider took too long to respond", 0, error)
                : new AiProviderException(AiProviderException.Kind.NETWORK,
                        "Cannot reach the AI provider: " + message, 0, error);
    }
}
