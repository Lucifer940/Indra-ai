package ai.indra.backend;

import ai.indra.backend.ai.AesSecretCipher;
import ai.indra.backend.ai.HttpProviderFactory;
import ai.indra.backend.ai.ServerKeyResolver;
import ai.indra.backend.http.ApiException;
import ai.indra.backend.http.Handler;
import ai.indra.backend.http.HttpServerApp;
import ai.indra.backend.http.RequestContext;
import ai.indra.backend.http.Response;
import ai.indra.backend.http.Router;
import ai.indra.backend.http.SseResponder;
import ai.indra.backend.service.AdminService;
import ai.indra.backend.service.AuthService;
import ai.indra.backend.service.ChatService;
import ai.indra.backend.service.DeviceService;
import ai.indra.backend.service.KeyService;
import ai.indra.backend.service.LicenseService;
import ai.indra.backend.service.MemoryService;
import ai.indra.backend.service.OAuthService;
import ai.indra.backend.service.SearchService;
import ai.indra.backend.service.SettingsService;
import ai.indra.backend.service.SyncService;
import ai.indra.backend.service.TaskService;
import ai.indra.backend.service.WeatherService;
import ai.indra.backend.store.DocumentStore;
import ai.indra.backend.store.Repository;
import ai.indra.backend.store.RepositoryKeyStore;
import ai.indra.backend.store.Stores;
import ai.indra.core.ai.ChatOrchestrator;
import ai.indra.core.apikeys.ApiKeyVault;
import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Log;

/**
 * Wires configuration, storage, services and routes into a running server.
 * Everything is constructed explicitly (no reflection or annotation scanning) so
 * the dependency graph is obvious and testable.
 */
public final class ApiApplication {

    private final BackendConfig config;
    private final DocumentStore store;
    private final Repository repository;
    private final ApiKeyVault vault;

    private final AuthService authService;
    private final MemoryService memoryService;
    private final TaskService taskService;
    private final ChatService chatService;
    private final SettingsService settingsService;
    private final KeyService keyService;
    private final DeviceService deviceService;
    private final SyncService syncService;
    private final AdminService adminService;
    private final LicenseService licenseService;
    private final WeatherService weatherService;
    private final SearchService searchService;
    private final OAuthService oauthService;
    private final HttpServerApp http;

    public ApiApplication(BackendConfig config) {
        this(config, ai.indra.backend.store.FileCodec.PLAIN);
    }

    /**
     * @param fileCodec optional at-rest encryption for the file store (Android
     *                  passes a Keystore-backed codec so on-device data is encrypted)
     */
    public ApiApplication(BackendConfig config, ai.indra.backend.store.FileCodec fileCodec) {
        this.config = config;
        Log.setMinimum(config.isDebug() ? Log.Level.DEBUG : Log.Level.INFO);
        this.store = Stores.create(config, fileCodec);
        this.repository = new Repository(store);
        this.vault = new ApiKeyVault(new RepositoryKeyStore(repository), new AesSecretCipher(config.getMasterKey()));

        ChatOrchestrator orchestrator = new ChatOrchestrator(
                new HttpProviderFactory(config), new ServerKeyResolver(vault, config));

        this.authService = new AuthService(repository, config);
        this.memoryService = new MemoryService(repository);
        this.taskService = new TaskService(repository);
        this.chatService = new ChatService(repository, orchestrator, memoryService, taskService);
        this.settingsService = new SettingsService(repository);
        this.keyService = new KeyService(vault);
        this.keyService.setAuditSink(new KeyService.AuditSink() {
            @Override
            public void record(ai.indra.core.model.User user, String action, String detail) {
                repository.audit(user.getId(), action, detail, null);
            }
        });
        this.deviceService = new DeviceService(repository, authService);
        this.syncService = new SyncService(repository);
        this.adminService = new AdminService(repository, config);
        this.licenseService = new LicenseService(repository);
        this.weatherService = new WeatherService(config);
        this.searchService = new SearchService(config);
        this.oauthService = new OAuthService(config, authService);

        this.http = new HttpServerApp(routes(), config, authService);
    }

    public void start() {
        authService.ensureBootstrapAdmin();
        try {
            http.start();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot start HTTP server on port " + config.getPort(), error);
        }
    }

    public void stop() {
        http.stop();
        store.close();
    }

    /** Port actually bound; when the config asked for port 0 the OS chose one. */
    public int getPort() {
        return http.getPort();
    }

    public boolean isRunning() {
        return http.isRunning();
    }

    public BackendConfig getConfig() {
        return config;
    }

    public Repository getRepository() {
        return repository;
    }

    private Router routes() {
        Router router = new Router();

        router.get("/", Router.Auth.PUBLIC, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.html(200, "<!doctype html><html><head><meta charset=\"utf-8\">"
                        + "<title>INDRA AI backend</title></head><body style=\"font-family:system-ui;"
                        + "background:#0b1020;color:#e8ecff;padding:40px\">"
                        + "<h1>INDRA AI backend</h1><p>Personal AI assistant by Umesh Chaudhary.</p>"
                        + "<p>Status: <strong>running</strong>. API base: <code>/api/v1</code></p>"
                        + "<p><a style=\"color:#8ab4ff\" href=\"/api/v1/health\">Health</a> · "
                        + "<a style=\"color:#8ab4ff\" href=\"/api/v1/config\">Remote config</a></p>"
                        + "</body></html>");
            }
        });

        router.get("/healthz", Router.Auth.PUBLIC, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                JsonObject body = new JsonObject();
                body.put("ok", Boolean.TRUE);
                body.put("status", "healthy");
                body.put("version", "1.1.0");
                return Response.ok(body);
            }
        });

        router.get("/api/v1/health", Router.Auth.PUBLIC, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(adminService.health());
            }
        });

        router.get("/api/v1/version", Router.Auth.PUBLIC, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(new JsonObject()
                        .put("ok", Boolean.TRUE)
                        .put("name", "INDRA AI")
                        .put("version", "1.1.0")
                        .put("creator", "Umesh Chaudhary"));
            }
        });

        router.get("/api/v1/config", Router.Auth.PUBLIC, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(new JsonObject()
                        .put("ok", Boolean.TRUE)
                        .put("config", repository.loadConfig().toJson()));
            }
        });

        // ------------------------------------------------------------- auth
        // On-device backend only: opens a session for this phone without an email
        // account. It exists so the app is usable immediately after install; real
        // accounts (and the licence) can be added later without losing data.
        if (config.isLocalMode()) {
            router.post("/api/v1/auth/local", Router.Auth.PUBLIC, new Handler() {
                @Override
                public Response handle(RequestContext context) {
                    return Response.ok(authService.localSession(context.body()));
                }
            });
        }

        router.post("/api/v1/auth/register", Router.Auth.PUBLIC, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.created(authService.register(context.body()));
            }
        });
        router.post("/api/v1/auth/login", Router.Auth.PUBLIC, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(authService.login(context.body()));
            }
        });
        router.post("/api/v1/auth/refresh", Router.Auth.PUBLIC, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(authService.refresh(context.body()));
            }
        });
        router.post("/api/v1/auth/logout", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                authService.logout(context.requireUser());
                return Response.ok(new JsonObject().put("ok", Boolean.TRUE).put("message", "Signed out"));
            }
        });
        router.get("/api/v1/auth/me", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(authService.profile(context.requireUser()));
            }
        });
        router.patch("/api/v1/auth/me", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(authService.updateProfile(context.requireUser(), context.body()));
            }
        });
        router.post("/api/v1/auth/password", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(authService.changePassword(context.requireUser(), context.body()));
            }
        });
        router.delete("/api/v1/auth/me", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(authService.deleteAccount(context.requireUser(), context.body()));
            }
        });
        router.get("/api/v1/auth/oauth/{provider}/start", Router.Auth.PUBLIC, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(oauthService.authorizeUrl(context.pathParam("provider"),
                        context.query("state", ""), context.query("redirectUri", ""), context.query("codeChallenge", "")));
            }
        });
        router.post("/api/v1/auth/oauth/{provider}/exchange", Router.Auth.PUBLIC, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                JsonObject body = context.body();
                return Response.ok(oauthService.exchange(context.pathParam("provider"),
                        body.getString("code"), body.getString("redirectUri"), body.getString("codeVerifier")));
            }
        });

        // ------------------------------------------------------------- chat
        router.post("/api/v1/chat", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(chatService.chat(context.requireUser(), context.body(),
                        context.body().getString("timeZone", "UTC")));
            }
        });
        router.post("/api/v1/chat/stream", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                SseResponder sse = (SseResponder) context.getAttribute("sse");
                chatService.stream(context.requireUser(), context.body(),
                        context.body().getString("timeZone", "UTC"), sse);
                return null; // response already streamed
            }
        });
        router.get("/api/v1/conversations", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(chatService.conversations(context.requireUser(),
                        Integer.parseInt(context.query("limit", "50"))));
            }
        });
        router.get("/api/v1/conversations/{id}/messages", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(chatService.messages(context.requireUser(), context.pathParam("id")));
            }
        });
        router.delete("/api/v1/conversations/{id}", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                repository.deleteConversation(context.getUserId(), context.pathParam("id"));
                return Response.ok(new JsonObject().put("ok", Boolean.TRUE).put("message", "Conversation deleted"));
            }
        });

        // ----------------------------------------------------------- memories
        router.get("/api/v1/memories", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(memoryService.list(context.requireUser(),
                        "true".equalsIgnoreCase(context.query("includeDeleted", "false"))));
            }
        });
        router.post("/api/v1/memories", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.created(memoryService.create(context.requireUser(), context.body()));
            }
        });
        router.put("/api/v1/memories/{id}", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(memoryService.update(context.requireUser(), context.pathParam("id"), context.body()));
            }
        });
        router.delete("/api/v1/memories/{id}", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(memoryService.delete(context.requireUser(), context.pathParam("id")));
            }
        });
        router.delete("/api/v1/memories", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(memoryService.clear(context.requireUser()));
            }
        });

        // -------------------------------------------------------------- tasks
        router.get("/api/v1/tasks", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(taskService.list(context.requireUser(),
                        "true".equalsIgnoreCase(context.query("includeCompleted", "false"))));
            }
        });
        router.post("/api/v1/tasks", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                JsonObject body = context.body();
                return Response.created(taskService.create(context.requireUser(), body,
                        body.getString("timeZone", "UTC")));
            }
        });
        router.put("/api/v1/tasks/{id}", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(taskService.update(context.requireUser(), context.pathParam("id"), context.body()));
            }
        });
        router.post("/api/v1/tasks/{id}/complete", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(taskService.complete(context.requireUser(), context.pathParam("id"),
                        context.body().getString("timeZone", "UTC")));
            }
        });
        router.delete("/api/v1/tasks/{id}", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(taskService.delete(context.requireUser(), context.pathParam("id")));
            }
        });

        // ----------------------------------------------------------- settings
        router.get("/api/v1/settings", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(settingsService.get(context.requireUser()));
            }
        });
        router.put("/api/v1/settings", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(settingsService.update(context.requireUser(), context.body()));
            }
        });
        router.get("/api/v1/settings/catalogue", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(settingsService.catalogue(context.requireUser()));
            }
        });

        // ----------------------------------------------------------- api keys
        router.get("/api/v1/keys", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(keyService.list(context.requireUser()));
            }
        });
        router.post("/api/v1/keys", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.created(keyService.add(context.requireUser(), context.body()));
            }
        });
        router.delete("/api/v1/keys/{id}", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(keyService.remove(context.requireUser(), context.pathParam("id")));
            }
        });
        router.post("/api/v1/keys/{id}/toggle", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(keyService.toggle(context.requireUser(), context.pathParam("id"), context.body()));
            }
        });
        router.post("/api/v1/keys/reset", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(keyService.reset(context.requireUser(), context.body()));
            }
        });

        // ------------------------------------------------------------ devices
        router.get("/api/v1/devices", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(deviceService.list(context.requireUser()));
            }
        });
        router.post("/api/v1/devices/pair/start", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.created(deviceService.startPairing(context.requireUser(), context.body()));
            }
        });
        router.post("/api/v1/devices/pair/complete", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(deviceService.completePairing(context.requireUser(), context.body()));
            }
        });
        router.post("/api/v1/devices/heartbeat", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(deviceService.heartbeat(context.requireUser(), context.body()));
            }
        });
        router.delete("/api/v1/devices/{id}", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(deviceService.revoke(context.requireUser(), context.pathParam("id")));
            }
        });

        // --------------------------------------------------------------- sync
        router.post("/api/v1/sync", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(syncService.sync(context.requireUser(), context.body()));
            }
        });

        // ------------------------------------------------------------ licence
        router.post("/api/v1/license/activate", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(licenseService.activate(context.requireUser(), context.body()));
            }
        });

        // --------------------------------------------------- web + weather
        router.get("/api/v1/weather", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                Double lat = context.query("lat") == null ? null : Double.valueOf(context.query("lat"));
                Double lon = context.query("lon") == null ? null : Double.valueOf(context.query("lon"));
                return Response.ok(weatherService.current(context.query("location", null), lat, lon));
            }
        });
        router.get("/api/v1/search", Router.Auth.USER, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(searchService.search(context.query("q", null),
                        Integer.parseInt(context.query("limit", "5")),
                        "true".equalsIgnoreCase(context.query("news", "false"))));
            }
        });

        // -------------------------------------------------------------- admin
        router.get("/api/v1/admin/users", Router.Auth.ADMIN, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(adminService.users(Integer.parseInt(context.query("limit", "50")),
                        Integer.parseInt(context.query("offset", "0")), context.query("q", null)));
            }
        });
        router.patch("/api/v1/admin/users/{id}", Router.Auth.ADMIN, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(adminService.updateUser(context.requireUser(), context.pathParam("id"), context.body()));
            }
        });
        router.delete("/api/v1/admin/users/{id}", Router.Auth.ADMIN, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(adminService.deleteUser(context.requireUser(), context.pathParam("id")));
            }
        });
        router.get("/api/v1/admin/config", Router.Auth.ADMIN, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(adminService.getConfig());
            }
        });
        router.put("/api/v1/admin/config", Router.Auth.ADMIN, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(adminService.updateConfig(context.requireUser(), context.body()));
            }
        });
        router.get("/api/v1/admin/audit", Router.Auth.ADMIN, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(adminService.audit(Integer.parseInt(context.query("limit", "100"))));
            }
        });
        router.get("/api/v1/admin/health", Router.Auth.ADMIN, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(adminService.health());
            }
        });
        router.get("/api/v1/admin/stats", Router.Auth.ADMIN, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(adminService.stats());
            }
        });
        router.get("/api/v1/admin/licenses", Router.Auth.ADMIN, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(licenseService.list(context.requireUser()));
            }
        });
        router.post("/api/v1/admin/licenses", Router.Auth.ADMIN, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.created(licenseService.issue(context.requireUser(), context.body()));
            }
        });
        router.post("/api/v1/admin/licenses/{key}/revoke", Router.Auth.ADMIN, new Handler() {
            @Override
            public Response handle(RequestContext context) {
                return Response.ok(licenseService.revoke(context.requireUser(), context.pathParam("key")));
            }
        });

        return router;
    }
}
