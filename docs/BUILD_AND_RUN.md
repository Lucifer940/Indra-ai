# Build and run INDRA AI

Everything below assumes **JDK 17 or newer** and **Gradle 8.x** on your PATH. The repository contains no
Gradle wrapper jar (it could not be downloaded in the authoring environment), so use your installed
`gradle` command, or generate a wrapper once with `gradle wrapper --gradle-version 8.7`.

| Component | Requirement |
|---|---|
| Core / backend / desktop | JDK 17+, Gradle 8.x. JavaFX 21 is resolved automatically by the desktop module. |
| Android app | JDK 17+, Gradle 8.x, Android SDK 34, build-tools, `ANDROID_HOME` set (or `local.properties` with `sdk.dir`). |
| Backend database | SQLite (bundled driver) for development; PostgreSQL 14+ for production. |

---

## 1. Backend

```bash
cd modules/indra-backend
gradle fatJar --no-daemon          # produces build/libs/indra-backend-all.jar
java -jar build/libs/indra-backend-all.jar version
java -jar build/libs/indra-backend-all.jar serve
```

Healthy start looks like:

```
INDRA AI backend 1.1.0
listening on http://0.0.0.0:8420
data directory: ./data
```

Verify:

```bash
curl http://127.0.0.1:8420/api/v1/health
curl http://127.0.0.1:8420/api/v1/version
```

### First administrator

```bash
java -jar build/libs/indra-backend-all.jar create-admin you@example.com
```

You are prompted for a password (minimum 8 characters). The account is stored hashed; no default password
exists anywhere in the repository. Alternatively set `INDRA_ADMIN_EMAIL` and `INDRA_ADMIN_PASSWORD` for the
**first** start only, then unset them.

### CLI commands

```
serve                                  start the HTTP server
create-admin <email> [password]        create or promote an administrator
issue-license [plan] [days]            generate a licence code
backup <file>                          export all collections to JSON
restore <file>                         restore a backup
license-keygen                         generate a random licence key
version | help
```

### Environment

Copy `config/backend.env.example` and export the values you need:

```
INDRA_PORT=8420                 INDRA_BIND=0.0.0.0
INDRA_PUBLIC_URL=https://ai.example.com
INDRA_DATA_DIR=./data
INDRA_JWT_SECRET=<32+ random chars>       # required in production
INDRA_MASTER_KEY=<32+ random chars>       # encrypts stored provider keys
INDRA_ADMIN_EMAIL / INDRA_ADMIN_PASSWORD  # first start only
INDRA_RATE_LIMIT=120            INDRA_AUTH_RATE_LIMIT=20
INDRA_CORS_ORIGINS=https://your-domains
INDRA_TLS_KEYSTORE=/path/keystore.p12  INDRA_TLS_KEYSTORE_PASSWORD=...
INDRA_DB_URL=jdbc:postgresql://localhost:5432/indra   INDRA_DB_USER / INDRA_DB_PASSWORD
OPENAI_API_KEY / DEEPSEEK_API_KEY / ... (see config/api-keys.md)
GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET
GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET
```

Production checklist: set `INDRA_JWT_SECRET`, `INDRA_MASTER_KEY`, your HTTPS domain, a PostgreSQL
`INDRA_DB_URL`, and disable debug. HTTPS is served directly (TLS keystore) or by a reverse proxy — both
work; the clients only need `https://` in their server field.

---

## 2. Windows desktop app

```bash
cd modules/indra-desktop
gradle run
```

Or build a runnable jar that bundles `indra-core`:

```bash
gradle jar
java -jar build/libs/indra-desktop-1.1.0.jar
```

Notes:
- Sign in with the account you created, or press **Start backend** on the login screen to launch
  `indra-backend-all.jar` from the current folder.
- Voice uses the Windows speech engine. Install a speech language in
  *Settings → Time & Language → Speech* if the engine is missing.
- Configuration lives in `%APPDATA%\IndraAI\client.properties`. It holds **no credentials**.

---

## 3. Android app

```bash
export ANDROID_HOME=$HOME/Android/Sdk          # Windows: set ANDROID_HOME=C:\Android\Sdk
cd modules/indra-android
gradle assembleDebug
adb install -r build/outputs/apk/debug/indra-android-debug.apk
```

Release build (you must create your own keystore — none is included):

```bash
keytool -genkeypair -v -keystore indra-release.jks -alias indra -keyalg RSA -keysize 2048 -validity 10000
gradle assembleRelease \
  -PINDRA_KEYSTORE=../indra-release.jks -PINDRA_KEY_ALIAS=indra \
  -PINDRA_KEYSTORE_PASSWORD=*** -PINDRA_KEY_PASSWORD=***
```

### Pointing the app at your server

- Emulator: `http://10.0.2.2:8420` (already the default).
- Real phone on the same Wi-Fi: `http://<your-PC-LAN-IP>:8420` — open **Settings → Server**, enter it, save.
  Android permits cleartext only for `10.0.2.2`, `127.0.0.1` and `localhost`; for a LAN IP you must either
  use HTTPS or add that host to `res/xml/network_security_config.xml` for personal testing.
- Production: `https://your-domain` — no extra configuration needed.

### Permissions the app may ask for

Microphone (voice), notifications (reminders), contacts (call/message by name), location (local weather),
SMS/phone (only when you confirm). Each is optional and only requested when the feature is used.

---

## 4. Tests

```bash
gradle test                            # core unit tests + backend integration tests
gradle :indra-core:test                # core only
gradle :indra-backend:test             # end-to-end HTTP tests (temp data dir, free port)
```

Android has no instrumented tests in this repository; UI behaviour is exercised manually per
`docs/TESTING.md`.

---

## 5. Packaging

```bash
bash scripts/make-release-zip.sh       # dist/indra-ai-v1.1.0-source.zip
```

The release script excludes build outputs, `node_modules`, IDE folders and any file matching `*.env`,
`*.jks` or `*.keystore`.
