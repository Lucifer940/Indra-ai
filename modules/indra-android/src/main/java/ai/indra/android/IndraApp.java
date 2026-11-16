package ai.indra.android;

import android.app.Application;
import android.content.Context;

import ai.indra.android.data.ApiClient;
import ai.indra.android.data.LocalStore;
import ai.indra.android.data.Prefs;
import ai.indra.android.data.SessionManager;
import ai.indra.android.service.NotificationHelper;
import ai.indra.core.apikeys.ApiKeyVault;
import ai.indra.core.util.Log;

/**
 * Application entry point. Holds the long-lived singletons (secure prefs, local
 * database, API client, session) so activities stay thin.
 */
public class IndraApp extends Application {

    private static IndraApp instance;
    private Prefs prefs;
    private LocalStore store;
    private ApiClient api;
    private SessionManager session;
    private ApiKeyVault keyVault;

    public static IndraApp get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.setMinimum(BuildConfig.DEBUG ? Log.Level.DEBUG : Log.Level.WARN);
        prefs = new Prefs(this);
        store = new LocalStore(this);
        api = new ApiClient(this, prefs);
        session = new SessionManager(api, prefs);
        keyVault = new ApiKeyVault(store, prefs.cipher());
        NotificationHelper.createChannels(this);
        api.setBaseUrl(prefs.getBaseUrl());
    }

    public static IndraApp from(Context context) {
        return (IndraApp) context.getApplicationContext();
    }

    public Prefs prefs() {
        return prefs;
    }

    public LocalStore store() {
        return store;
    }

    public ApiClient api() {
        return api;
    }

    public SessionManager session() {
        return session;
    }

    public ApiKeyVault keyVault() {
        return keyVault;
    }
}
