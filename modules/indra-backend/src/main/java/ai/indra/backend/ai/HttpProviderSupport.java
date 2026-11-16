package ai.indra.backend.ai;

import ai.indra.core.ai.AiProviderException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Shared HTTP plumbing for provider clients. */
final class HttpProviderSupport {

    static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpProviderSupport() {
    }

    static HttpResponse<String> postJson(String url, String body, String... headers) throws AiProviderException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "IndraAI/1.1.0 (+https://github.com/Lucifer940/Indra-ai)")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            if (headers[i] != null && headers[i + 1] != null && !headers[i + 1].isEmpty()) {
                builder.header(headers[i], headers[i + 1]);
            }
        }
        try {
            return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException error) {
            throw new AiProviderException(AiProviderException.Kind.TIMEOUT, "The AI provider took too long to respond");
        } catch (IOException error) {
            throw new AiProviderException(AiProviderException.Kind.NETWORK,
                    "Cannot reach the AI provider: " + error.getMessage(), 0, error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(AiProviderException.Kind.NETWORK, "Request interrupted", 0, error);
        }
    }

    static HttpRequest.Builder requestBuilder(String url, String... headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("User-Agent", "IndraAI/1.1.0")
                .POST(HttpRequest.BodyPublishers.noBody());
        for (int i = 0; i + 1 < headers.length; i += 2) {
            if (headers[i] != null && headers[i + 1] != null && !headers[i + 1].isEmpty()) {
                builder.header(headers[i], headers[i + 1]);
            }
        }
        return builder;
    }

    static void checkStatus(HttpResponse<String> response, String providerLabel) throws AiProviderException {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        String detail = response.body() == null ? "" : response.body();
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
}
