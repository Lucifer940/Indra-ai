package ai.indra.backend.service;

import ai.indra.backend.BackendConfig;
import ai.indra.backend.http.ApiException;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Strings;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Web search and news lookup.
 *
 * <p>Uses Tavily when {@code TAVILY_API_KEY} is configured (good for fresh news),
 * otherwise the Wikipedia API which needs no key. The response always names the
 * source and includes links so the assistant can attribute facts and never
 * invents them.</p>
 */
public final class SearchService {

    private final BackendConfig config;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SearchService(BackendConfig config) {
        this.config = config;
    }

    public JsonObject search(String query, int limit, boolean news) {
        if (Strings.isBlank(query)) {
            throw ApiException.badRequest("What should I search for?");
        }
        String tavilyKey = config.getTavilyKey();
        if (tavilyKey != null) {
            try {
                return tavily(query, limit, news, tavilyKey);
            } catch (ApiException error) {
                // Fall through to the keyless source rather than failing the request.
                ai.indra.core.util.Log.warn("SearchService", "Tavily failed: " + error.getMessage());
            }
        }
        return wikipedia(query, limit);
    }

    private JsonObject tavily(String query, int limit, boolean news, String apiKey) {
        JsonObject payload = new JsonObject();
        payload.put("api_key", apiKey);
        payload.put("query", query);
        payload.put("max_results", Integer.valueOf(Math.max(1, Math.min(10, limit))));
        payload.put("search_depth", "basic");
        if (news) {
            payload.put("topic", "news");
            payload.put("days", Integer.valueOf(7));
        }
        JsonObject data = postJson("https://api.tavily.com/search", payload);
        JsonArray results = new JsonArray();
        JsonArray raw = data.getArrayOrEmpty("results");
        for (int i = 0; i < raw.size(); i++) {
            JsonObject item = raw.getObject(i);
            if (item == null) {
                continue;
            }
            results.add(new JsonObject()
                    .put("title", item.getString("title", ""))
                    .put("url", item.getString("url", ""))
                    .put("snippet", item.getString("content", ""))
                    .put("published", item.getString("published_date", "")));
        }
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("query", query);
        response.put("results", results);
        response.put("source", "Tavily");
        response.put("note", news ? "Recent news results - check the links before relying on them."
                : "Search results with source links.");
        return response;
    }

    private JsonObject wikipedia(String query, int limit) {
        String searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit="
                + Math.max(1, Math.min(10, limit)) + "&srsearch=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        JsonObject data = getJson(searchUrl);
        JsonArray search = data.getObjectOrEmpty("query").getArrayOrEmpty("search");
        JsonArray results = new JsonArray();
        for (int i = 0; i < search.size(); i++) {
            JsonObject item = search.getObject(i);
            if (item == null) {
                continue;
            }
            String title = item.getString("title", "");
            String snippet = item.getString("snippet", "").replaceAll("<[^>]+>", "").trim();
            results.add(new JsonObject()
                    .put("title", title)
                    .put("url", "https://en.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8))
                    .put("snippet", snippet)
                    .put("published", ""));
        }
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("query", query);
        response.put("results", results);
        response.put("source", "Wikipedia");
        response.put("note", "Wikipedia is used because no news search key is configured. "
                + "For live news, set TAVILY_API_KEY on the server.");
        return response;
    }

    private JsonObject getJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "IndraAI/1.1.0 (personal assistant)")
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(502, "search_unavailable", "Search is unavailable right now");
            }
            return JsonObject.parse(response.body());
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException(502, "search_unavailable", "I can't reach the search service right now");
        }
    }

    private JsonObject postJson(String url, JsonObject payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "IndraAI/1.1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toJson(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(502, "search_unavailable", "Search provider error " + response.statusCode());
            }
            return JsonObject.parse(response.body());
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException(502, "search_unavailable", "I can't reach the search service right now");
        }
    }
}
