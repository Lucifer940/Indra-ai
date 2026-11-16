package ai.indra.backend.service;

import ai.indra.backend.BackendConfig;
import ai.indra.backend.http.ApiException;
import ai.indra.core.net.Http;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Strings;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Weather via Open-Meteo (no API key required) with geocoding. Results always say
 * where they came from so the assistant can attribute them honestly.
 */
public final class WeatherService {

    private final BackendConfig config;

    public WeatherService(BackendConfig config) {
        this.config = config;
    }

    public JsonObject current(String location, Double latitude, Double longitude) {
        double lat;
        double lon;
        String place;
        if (latitude != null && longitude != null) {
            lat = latitude.doubleValue();
            lon = longitude.doubleValue();
            place = location == null ? "your location" : location;
        } else {
            JsonObject geo = geocode(location);
            lat = geo.getDouble("latitude", 0d);
            lon = geo.getDouble("longitude", 0d);
            place = geo.getString("name", Strings.orEmpty(location)) + (geo.getString("country") == null
                    ? "" : ", " + geo.getString("country"));
        }
        String url = config.getWeatherBaseUrl() + "/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,"
                + "weather_code,wind_speed_10m"
                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code"
                + "&timezone=auto&forecast_days=5";
        JsonObject data = getJson(url);
        JsonObject current = data.getObjectOrEmpty("current");
        JsonObject daily = data.getObjectOrEmpty("daily");
        JsonArray forecast = new JsonArray();
        JsonArray dates = daily.getArrayOrEmpty("time");
        JsonArray max = daily.getArrayOrEmpty("temperature_2m_max");
        JsonArray min = daily.getArrayOrEmpty("temperature_2m_min");
        JsonArray rain = daily.getArrayOrEmpty("precipitation_probability_max");
        for (int i = 0; i < dates.size() && i < 5; i++) {
            forecast.add(new JsonObject()
                    .put("date", dates.getString(i, ""))
                    .put("maxC", Double.valueOf(max.getDouble(i, 0d)))
                    .put("minC", Double.valueOf(min.getDouble(i, 0d)))
                    .put("rainChance", Integer.valueOf((int) rain.getLong(i, 0L)))
                    .put("summary", describeCode((int) daily.getArrayOrEmpty("weather_code").getLong(i, 0L))));
        }
        int code = (int) current.getLong("weather_code", 0L);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("location", place);
        response.put("temperatureC", Double.valueOf(current.getDouble("temperature_2m", 0d)));
        response.put("feelsLikeC", Double.valueOf(current.getDouble("apparent_temperature", 0d)));
        response.put("humidity", Integer.valueOf((int) current.getLong("relative_humidity_2m", 0L)));
        response.put("windKph", Double.valueOf(current.getDouble("wind_speed_10m", 0d)));
        response.put("precipitationMm", Double.valueOf(current.getDouble("precipitation", 0d)));
        response.put("summary", describeCode(code));
        response.put("forecast", forecast);
        response.put("source", "Open-Meteo (open-meteo.com)");
        return response;
    }

    public JsonObject geocode(String location) {
        if (Strings.isBlank(location)) {
            throw ApiException.badRequest("Tell me a city or enable location access");
        }
        String url = config.getGeocodeBaseUrl() + "/search?name="
                + URLEncoder.encode(location.trim(), StandardCharsets.UTF_8) + "&count=1&language=en&format=json";
        JsonObject data = getJson(url);
        JsonArray results = data.getArrayOrEmpty("results");
        if (results.isEmpty()) {
            throw ApiException.notFound("I couldn't find a place called \"" + location + "\"");
        }
        JsonObject first = results.getObject(0);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("name", first.getString("name", location));
        response.put("country", first.getString("country", ""));
        response.put("latitude", Double.valueOf(first.getDouble("latitude", 0d)));
        response.put("longitude", Double.valueOf(first.getDouble("longitude", 0d)));
        response.put("timezone", first.getString("timezone", "UTC"));
        return response;
    }

    private JsonObject getJson(String url) {
        try {
            Http.Response response = Http.get(url, 20000, "User-Agent", "IndraAI/1.2.0");
            try {
                if (!response.isSuccessful()) {
                    throw new ApiException(502, "weather_unavailable",
                            "The weather service is unavailable right now (status " + response.statusCode() + ")");
                }
                return JsonObject.parse(response.body());
            } finally {
                response.close();
            }
        } catch (IOException error) {
            throw new ApiException(502, "weather_unavailable", "I can't reach the weather service right now");
        }
    }

    private String describeCode(int code) {
        if (code == 0) {
            return "Clear sky";
        }
        if (code <= 3) {
            return "Partly cloudy";
        }
        if (code <= 48) {
            return "Fog";
        }
        if (code <= 57) {
            return "Drizzle";
        }
        if (code <= 67) {
            return "Rain";
        }
        if (code <= 77) {
            return "Snow";
        }
        if (code <= 82) {
            return "Rain showers";
        }
        if (code <= 86) {
            return "Snow showers";
        }
        return "Thunderstorm";
    }
}
