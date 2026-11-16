package ai.indra.backend.service;

import ai.indra.backend.BackendConfig;
import ai.indra.backend.http.ApiException;
import ai.indra.backend.http.HttpServerApp;
import ai.indra.backend.store.Repository;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.AuthProvider;
import ai.indra.core.model.Role;
import ai.indra.core.model.User;
import ai.indra.core.security.Jwt;
import ai.indra.core.security.PasswordHasher;
import ai.indra.core.security.Validation;
import ai.indra.core.util.Log;
import ai.indra.core.util.Strings;

/** Registration, login, sessions, profile and account deletion. */
public final class AuthService implements HttpServerApp.Authenticator {

    private static final long ACCESS_TTL_SECONDS = 60L * 60L;
    private static final long REFRESH_TTL_SECONDS = 60L * 60L * 24L * 30L;

    private final Repository repository;
    private final BackendConfig config;
    private final PasswordHasher hasher = new PasswordHasher();
    private final Jwt accessTokens;
    private final Jwt refreshTokens;

    public AuthService(Repository repository, BackendConfig config) {
        this.repository = repository;
        this.config = config;
        this.accessTokens = new Jwt(config.getJwtSecret(), "indra-ai", ACCESS_TTL_SECONDS);
        this.refreshTokens = new Jwt(config.getJwtSecret(), "indra-ai-refresh", REFRESH_TTL_SECONDS);
    }

    // --------------------------------------------------------------- sessions

    /** Creates the admin account declared through environment variables, once. */
    public void ensureBootstrapAdmin() {
        String email = config.getBootstrapAdminEmail();
        String password = config.getBootstrapAdminPassword();
        if (email == null || password == null) {
            if (repository.countUsers() == 0) {
                Log.warn("AuthService", "No users yet. Either register in the app (first account becomes administrator) "
                        + "or start with INDRA_ADMIN_EMAIL / INDRA_ADMIN_PASSWORD for a controlled bootstrap.");
            }
            return;
        }
        User existing = repository.findUserByEmail(email);
        if (existing != null) {
            if (!existing.isAdmin()) {
                existing.setRole(Role.ADMIN);
                repository.saveUser(existing);
                Log.info("AuthService", "Promoted bootstrap account to administrator: " + email);
            }
            return;
        }
        try {
            User admin = User.newUser(Validation.normalizeEmail(email),
                    Validation.requireText("displayName", "Administrator", 1, 60),
                    hasher.hash(Validation.requirePassword("password", password)));
            admin.setRole(Role.ADMIN);
            admin.setEmailVerified(true);
            repository.saveUser(admin);
            repository.audit(admin.getId(), "admin.bootstrap", "Administrator created from environment", "local");
            Log.info("AuthService", "Bootstrap administrator created: " + email
                    + " - change the password after first login and remove INDRA_ADMIN_PASSWORD from the environment.");
        } catch (RuntimeException error) {
            Log.error("AuthService", "Bootstrap admin failed: " + error.getMessage());
        }
    }

    /**
     * Device session for the on-device backend ("continue without an account").
     *
     * <p>The account is derived from the installation's device id, gets a random
     * password that is never shown or stored, and can later be upgraded by setting
     * an email and password through the normal account screen. Nothing secret is
     * hard-coded: the password is generated per install and only its hash is kept.</p>
     */
    public JsonObject localSession(JsonObject body) {
        if (!config.isLocalMode()) {
            throw ApiException.forbidden("Device sessions are only available in the on-device backend");
        }
        String deviceId = body == null ? null : body.getString("deviceId");
        if (Strings.isBlank(deviceId) || deviceId.length() < 8) {
            throw ApiException.badRequest("A device id is required");
        }
        String digest = ai.indra.core.security.Crypto.sha256Hex(deviceId);
        String email = "device-" + digest.substring(0, 24) + "@local.indra";
        String displayName = body.getString("displayName", "");
        User user = repository.findUserByEmail(email);
        if (user == null) {
            String generated = "local-" + digest.substring(0, 16) + "-" + ai.indra.core.util.Ids.random(24);
            String name = Strings.isBlank(displayName) ? "INDRA user" : displayName.trim();
            user = User.newUser(email, Validation.requireText("displayName", name, 1, 60),
                    hasher.hash(generated));
            user.setEmailVerified(true);
            repository.saveUser(user);
            repository.audit(user.getId(), "auth.local", "On-device session created", "device");
        }
        if (user.isDisabled()) {
            throw ApiException.forbidden("This local account has been disabled");
        }
        user.setLastLoginAt(System.currentTimeMillis());
        repository.saveUser(user);
        return session(user);
    }

    public JsonObject register(JsonObject body) {
        String email = Validation.requireEmail("email", body.getString("email"));
        String password = Validation.requirePassword("password", body.getString("password"));
        String displayName = body.getString("displayName");
        if (Strings.isBlank(displayName)) {
            int at = email.indexOf('@');
            displayName = at > 0 ? email.substring(0, at) : "Friend";
        }
        displayName = Validation.requireText("displayName", displayName, 1, 60);
        ai.indra.core.config.RemoteConfig remote = repository.loadConfig();
        if (!remote.isRegistrationOpen()) {
            throw ApiException.forbidden("New registrations are currently closed by the administrator");
        }
        if (repository.findUserByEmail(email) != null) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = User.newUser(email, displayName, hasher.hash(password));
        // The very first account on a fresh server becomes the administrator.
        if (repository.countUsers() == 0) {
            user.setRole(Role.ADMIN);
            user.setEmailVerified(true);
            Log.warn("AuthService", "First account registered as administrator: " + email);
        }
        user.setLastLoginAt(System.currentTimeMillis());
        repository.saveUser(user);
        repository.audit(user.getId(), "auth.register", "Account created (" + user.getRole() + ")", null);
        return session(user);
    }

    public JsonObject login(JsonObject body) {
        String email = Validation.normalizeEmail(body.getString("email"));
        String password = body.getString("password");
        if (Strings.isBlank(email) || password == null) {
            throw ApiException.badRequest("Email and password are required");
        }
        User user = repository.findUserByEmail(email);
        if (user == null) {
            // Same message and work factor as a wrong password to avoid user enumeration.
            hasher.verify(hasher.hash("dummy-password-for-timing"), password);
            throw ApiException.unauthorized("Incorrect email or password");
        }
        if (!hasher.verify(user.getPasswordHash(), password)) {
            repository.audit(user.getId(), "auth.login.failed", "Wrong password", null);
            throw ApiException.unauthorized("Incorrect email or password");
        }
        if (user.isDisabled()) {
            throw ApiException.forbidden("This account has been disabled by the administrator");
        }
        if (hasher.needsRehash(user.getPasswordHash())) {
            user.setPasswordHash(hasher.hash(password));
        }
        user.setLastLoginAt(System.currentTimeMillis());
        repository.saveUser(user);
        repository.audit(user.getId(), "auth.login", "Password login", null);
        return session(user);
    }

    public JsonObject refresh(JsonObject body) {
        String token = body.getString("refreshToken");
        JsonObject claims;
        try {
            claims = refreshTokens.verify(token);
        } catch (Jwt.TokenException error) {
            throw ApiException.unauthorized("Session expired - please sign in again");
        }
        String userId = claims.getString("sub");
        User user = repository.findUser(userId);
        if (user == null || user.isDisabled()) {
            throw ApiException.unauthorized("Account is no longer available");
        }
        long tokenVersion = Long.parseLong(repository.getMeta("tokenVersion:" + userId, "0"));
        if (claims.getLong("tv", 0L) != tokenVersion) {
            throw ApiException.unauthorized("Session revoked - please sign in again");
        }
        return session(user);
    }

    /** Invalidates every existing token for the user. */
    public void logout(User user) {
        bumpTokenVersion(user.getId());
        repository.audit(user.getId(), "auth.logout", "Sessions revoked", null);
    }

    private void bumpTokenVersion(String userId) {
        long current = Long.parseLong(repository.getMeta("tokenVersion:" + userId, "0"));
        repository.setMeta("tokenVersion:" + userId, String.valueOf(current + 1));
    }

    private JsonObject session(User user) {
        long tokenVersion = Long.parseLong(repository.getMeta("tokenVersion:" + user.getId(), "0"));
        JsonObject claims = new JsonObject();
        claims.put("sub", user.getId());
        claims.put("role", user.getRole().name());
        claims.put("email", user.getEmail());
        claims.put("tv", Long.valueOf(tokenVersion));
        claims.put("typ", "access");
        String accessToken = accessTokens.sign(claims);

        JsonObject refreshClaims = new JsonObject(claims);
        refreshClaims.put("typ", "refresh");
        String refreshToken = refreshTokens.sign(refreshClaims);

        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("expiresIn", Long.valueOf(ACCESS_TTL_SECONDS));
        response.put("user", user.toJson());
        JsonObject settings = repository.loadSettings(user.getId()).toJson();
        response.put("settings", settings);
        return response;
    }

    // ------------------------------------------------------------ authentication

    @Override
    public User authenticate(String token) {
        JsonObject claims;
        try {
            claims = accessTokens.verify(token);
        } catch (Jwt.TokenException error) {
            throw ApiException.unauthorized("Session expired - please sign in again");
        }
        if (!"access".equals(claims.getString("typ", "access"))) {
            throw ApiException.unauthorized("Use the access token for this request");
        }
        String userId = claims.getString("sub");
        User user = repository.findUser(userId);
        if (user == null) {
            throw ApiException.unauthorized("Account not found");
        }
        if (user.isDisabled()) {
            throw ApiException.forbidden("This account has been disabled");
        }
        long tokenVersion = Long.parseLong(repository.getMeta("tokenVersion:" + userId, "0"));
        if (claims.getLong("tv", 0L) != tokenVersion) {
            throw ApiException.unauthorized("Session revoked - please sign in again");
        }
        return user;
    }

    // ---------------------------------------------------------------- profile

    public JsonObject profile(User user) {
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("user", user.toJson());
        response.put("settings", repository.loadSettings(user.getId()).toJson());
        response.put("memories", Integer.valueOf(repository.listMemories(user.getId(), false).size()));
        response.put("tasks", Integer.valueOf(repository.listTasks(user.getId(), false).size()));
        response.put("devices", Integer.valueOf(repository.listDevices(user.getId()).size()));
        response.put("licence", user.getLicenseKey());
        return response;
    }

    public JsonObject updateProfile(User user, JsonObject body) {
        if (body.has("displayName")) {
            user.setDisplayName(Validation.requireText("displayName", body.getString("displayName"), 1, 60));
        }
        if (body.has("photoUrl")) {
            user.setPhotoUrl(Validation.optionalText("photoUrl", body.getString("photoUrl"), 500));
        }
        repository.saveUser(user);
        repository.audit(user.getId(), "profile.update", "Profile updated", null);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("user", user.toJson());
        return response;
    }

    public JsonObject changePassword(User user, JsonObject body) {
        String current = body.getString("currentPassword");
        String next = Validation.requirePassword("newPassword", body.getString("newPassword"));
        if (!hasher.verify(user.getPasswordHash(), current == null ? "" : current)) {
            throw ApiException.unauthorized("Current password is incorrect");
        }
        if (hasher.verify(user.getPasswordHash(), next)) {
            throw ApiException.badRequest("Choose a password you have not used before");
        }
        user.setPasswordHash(hasher.hash(next));
        repository.saveUser(user);
        bumpTokenVersion(user.getId());
        repository.audit(user.getId(), "auth.password.change", "Password changed", null);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("message", "Password updated. Please sign in again on your devices.");
        return response;
    }

    public JsonObject deleteAccount(User user, JsonObject body) {
        String password = body.getString("password");
        if (user.getProvider() == AuthProvider.LOCAL) {
            if (!hasher.verify(user.getPasswordHash(), password == null ? "" : password)) {
                throw ApiException.unauthorized("Password is incorrect");
            }
        }
        repository.audit(null, "account.delete", "Account " + user.getEmail() + " deleted", null);
        repository.getStore().deleteUserData(user.getId());
        repository.getStore().delete(Repository.USERS, user.getId());
        repository.setMeta("tokenVersion:" + user.getId(), "deleted");
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("message", "Your account and all stored data have been deleted");
        return response;
    }

    /** Used by the OAuth flow to find-or-create a federated account. */
    public User upsertFederatedUser(AuthProvider provider, String subject, String email, String name, String photoUrl) {
        if (Strings.isBlank(email)) {
            throw ApiException.badRequest("The identity provider did not share an email address");
        }
        String normalizedEmail = Validation.normalizeEmail(email);
        User user = repository.findUserByEmail(normalizedEmail);
        if (user == null) {
            user = User.newUser(normalizedEmail,
                    Strings.isBlank(name) ? normalizedEmail.substring(0, normalizedEmail.indexOf('@')) : Validation.requireText("displayName", name, 1, 60),
                    hasher.hash(ai.indra.core.security.Crypto.randomToken(24)));
            user.setProvider(provider);
            user.setProviderSubject(subject);
            user.setEmailVerified(true);
            user.setPhotoUrl(photoUrl);
            if (repository.countUsers() == 0) {
                user.setRole(Role.ADMIN);
            }
            user.setLastLoginAt(System.currentTimeMillis());
            repository.saveUser(user);
            repository.audit(user.getId(), "auth.oauth.register", provider + " account created", null);
        } else {
            if (user.getProvider() == AuthProvider.LOCAL && !provider.name().equals(user.getProvider().name())) {
                // Link the federated identity to the existing local account.
                user.setProvider(provider);
                user.setProviderSubject(subject);
            }
            if (user.isDisabled()) {
                throw ApiException.forbidden("This account has been disabled");
            }
            if (photoUrl != null && Strings.isBlank(user.getPhotoUrl())) {
                user.setPhotoUrl(photoUrl);
            }
            user.setLastLoginAt(System.currentTimeMillis());
            repository.saveUser(user);
            repository.audit(user.getId(), "auth.oauth.login", provider + " login", null);
        }
        return user;
    }

    public JsonObject federatedSession(User user) {
        return session(user);
    }
}
