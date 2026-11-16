package ai.indra.backend.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small path router with {param} support and per-route auth levels. */
public final class Router {

    public enum Auth {
        PUBLIC,
        USER,
        ADMIN
    }

    /** A registered route. */
    public static final class Route {
        final String method;
        final String[] segments;
        final Auth auth;
        final Handler handler;

        Route(String method, String[] segments, Auth auth, Handler handler) {
            this.method = method;
            this.segments = segments;
            this.auth = auth;
            this.handler = handler;
        }

        public Auth getAuth() {
            return auth;
        }
    }

    /** A resolved route plus extracted path parameters. */
    public static final class Match {
        public final Route route;
        public final Map<String, String> params;

        Match(Route route, Map<String, String> params) {
            this.route = route;
            this.params = params;
        }
    }

    private final List<Route> routes = new ArrayList<Route>();

    public Router get(String pattern, Auth auth, Handler handler) {
        return add("GET", pattern, auth, handler);
    }

    public Router post(String pattern, Auth auth, Handler handler) {
        return add("POST", pattern, auth, handler);
    }

    public Router put(String pattern, Auth auth, Handler handler) {
        return add("PUT", pattern, auth, handler);
    }

    public Router patch(String pattern, Auth auth, Handler handler) {
        return add("PATCH", pattern, auth, handler);
    }

    public Router delete(String pattern, Auth auth, Handler handler) {
        return add("DELETE", pattern, auth, handler);
    }

    public Router add(String method, String pattern, Auth auth, Handler handler) {
        routes.add(new Route(method.toUpperCase(java.util.Locale.ROOT), split(pattern),
                auth == null ? Auth.PUBLIC : auth, handler));
        return this;
    }

    private static String[] split(String pattern) {
        String cleaned = pattern.startsWith("/") ? pattern.substring(1) : pattern;
        if (cleaned.endsWith("/") && cleaned.length() > 1) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.isEmpty() ? new String[0] : cleaned.split("/");
    }

    /** Finds a route; returns null when nothing matches the path. */
    public Match match(String method, String path) {
        String[] parts = split(path);
        Match methodMismatch = null;
        for (Route route : routes) {
            Map<String, String> params = new LinkedHashMap<String, String>();
            if (!matches(route.segments, parts, params)) {
                continue;
            }
            Match candidate = new Match(route, params);
            if (route.method.equalsIgnoreCase(method)) {
                return candidate;
            }
            if (methodMismatch == null) {
                methodMismatch = candidate;
            }
        }
        return methodMismatch;
    }

    private static boolean matches(String[] pattern, String[] actual, Map<String, String> params) {
        if (pattern.length != actual.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            String segment = pattern[i];
            if (segment.startsWith("{") && segment.endsWith("}")) {
                String name = segment.substring(1, segment.length() - 1);
                String value = decode(actual[i]);
                if (value == null || value.isEmpty()) {
                    return false;
                }
                params.put(name, value);
            } else if (!segment.equals(actual[i])) {
                return false;
            }
        }
        return true;
    }

    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException error) {
            return value;
        }
    }

    public int size() {
        return routes.size();
    }
}
