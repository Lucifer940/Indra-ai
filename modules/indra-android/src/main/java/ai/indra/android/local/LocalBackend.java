package ai.indra.android.local;

import android.content.Context;

import ai.indra.android.BuildConfig;
import ai.indra.android.data.KeystoreCipher;
import ai.indra.android.data.Prefs;
import ai.indra.backend.ApiApplication;
import ai.indra.backend.BackendConfig;
import ai.indra.core.util.Log;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The INDRA backend running inside the phone.
 *
 * <p>The same {@link ApiApplication} that serves the Windows/desktop deployment is
 * started here on {@code 127.0.0.1} with an operating-system assigned port. The app's
 * HTTP client talks to it exactly as it would talk to a PC, so REST routes,
 * authentication, streaming (SSE), rate limiting and error handling are identical
 * and nothing had to be re-implemented for Android.</p>
 *
 * <p>Properties:</p>
 * <ul>
 *   <li>loopback only - the port is not reachable from the LAN;</li>
 *   <li>data lives in the app sandbox and is encrypted with the Keystore key;</li>
 *   <li>started from {@link ai.indra.android.IndraApp}, so every activity (and the
 *       boot receiver after a reboot) finds it ready;</li>
 *   <li>if it cannot start, the app falls back to the "no engine" state and says so
 *       instead of pretending to work.</li>
 * </ul>
 */
public final class LocalBackend {

    private static final String ENGINE_DIR = "engine";

    private static LocalBackend instance;

    private final CountDownLatch ready = new CountDownLatch(1);
    private final Object startLock = new Object();

    private volatile Context context;
    private volatile ApiApplication application;
    private volatile String baseUrl;
    private volatile String failure;
    private volatile boolean starting;

    private LocalBackend(Context context) {
        this.context = context.getApplicationContext();
    }

    public static LocalBackend get(Context context) {
        if (instance == null) {
            synchronized (LocalBackend.class) {
                if (instance == null) {
                    instance = new LocalBackend(context);
                }
            }
        }
        return instance;
    }

    /** Starts the engine on a background thread; safe to call repeatedly. */
    public void start() {
        synchronized (startLock) {
            if (starting || baseUrl != null) {
                return;
            }
            starting = true;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                boot();
            }
        }, "indra-local-engine");
        thread.setDaemon(true);
        thread.start();
    }

    private void boot() {
        long started = System.currentTimeMillis();
        try {
            Context app = context;
            if (app == null) {
                failure = "No application context";
                return;
            }
            Prefs prefs = new Prefs(app);
            File dataDir = new File(app.getFilesDir(), ENGINE_DIR);
            if (!dataDir.exists() && !dataDir.mkdirs()) {
                failure = "Cannot create " + dataDir.getAbsolutePath();
                return;
            }

            Map<String, String> values = new LinkedHashMap<String, String>();
            values.put("INDRA_DATA_DIR", dataDir.getAbsolutePath());
            values.put("INDRA_BIND", "127.0.0.1");
            values.put("INDRA_PORT", "0");
            values.put("INDRA_LOCAL_MODE", "true");
            values.put("INDRA_DEBUG", String.valueOf(BuildConfig.DEBUG));
            values.put("INDRA_RATE_LIMIT", "6000");
            values.put("INDRA_AUTH_RATE_LIMIT", "600");
            // Secrets are random per install and stored encrypted by the Keystore.
            values.put("INDRA_JWT_SECRET", prefs.getOrCreateLocalSecret("jwt"));
            values.put("INDRA_MASTER_KEY", prefs.getOrCreateLocalSecret("master"));
            values.put("INDRA_CORS_ORIGINS", "*");

            BackendConfig config = BackendConfig.fromMap(values);
            KeystoreFileCodec codec = new KeystoreFileCodec(new KeystoreCipher());
            ApiApplication app0 = new ApiApplication(config, codec);
            app0.start();
            application = app0;
            baseUrl = "http://127.0.0.1:" + app0.getPort();
            Log.info("LocalBackend", "On-device engine ready at " + baseUrl
                    + " in " + (System.currentTimeMillis() - started) + "ms");
        } catch (Throwable error) {
            failure = error.getClass().getSimpleName() + ": " + error.getMessage();
            Log.error("LocalBackend", "On-device engine failed to start", error);
        } finally {
            ready.countDown();
        }
    }

    /** Base URL of the embedded engine, or null when it is not available. */
    public String baseUrl() {
        if (baseUrl == null) {
            awaitReady(15000);
        }
        return baseUrl;
    }

    /** Waits up to {@code millis} for the engine, returning its URL or null. */
    public String awaitReady(int millis) {
        try {
            ready.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        return baseUrl;
    }

    public boolean isRunning() {
        return application != null && application.isRunning();
    }

    public int port() {
        return application == null ? 0 : application.getPort();
    }

    /** Human-readable state for the settings screen. */
    public String describe() {
        if (baseUrl != null) {
            return "On-device engine running on port " + port();
        }
        if (failure != null) {
            return "On-device engine unavailable: " + failure;
        }
        return starting ? "On-device engine starting" : "On-device engine stopped";
    }

    /** Last failure message, or null. */
    public String failure() {
        return failure;
    }

    public void stop() {
        ApiApplication current = application;
        application = null;
        baseUrl = null;
        if (current != null) {
            try {
                current.stop();
            } catch (RuntimeException error) {
                Log.warn("LocalBackend", "Stop failed: " + error.getMessage());
            }
        }
    }
}
