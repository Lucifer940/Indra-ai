package ai.indra.backend.http;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Simple fixed-window limiter keyed by client and bucket. */
public final class RateLimiter {

    private static final class Window {
        long windowStart;
        int count;
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<String, Window>();
    private final int limitPerMinute;
    private final long windowMillis = 60_000L;

    public RateLimiter(int limitPerMinute) {
        this.limitPerMinute = Math.max(10, limitPerMinute);
    }

    /** Records a hit; returns false when the caller is over the limit. */
    public boolean allow(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.get(key);
        if (window == null) {
            window = new Window();
            window.windowStart = now;
            windows.put(key, window);
        }
        synchronized (window) {
            if (now - window.windowStart >= windowMillis) {
                window.windowStart = now;
                window.count = 0;
            }
            window.count++;
            return window.count <= limitPerMinute;
        }
    }

    /** Drops stale buckets so memory does not grow forever. */
    public void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().windowStart > windowMillis * 5L) {
                iterator.remove();
            }
        }
    }

    public int getLimit() {
        return limitPerMinute;
    }
}
