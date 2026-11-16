# Build and run INDRA AI

Everything below assumes **JDK 17 or newer**. The repository ships the **Gradle 8.7 wrapper**
(`gradlew`, `gradlew.bat`, `gradle/wrapper/*`), so no local Gradle installation is required — just use
`./gradlew` (macOS/Linux/Git Bash) or `gradlew.bat` (Windows). The wrapper downloads Gradle 8.7 on first use.

| Component | Requirement |
|---|---|
| Core / backend / desktop | JDK 17+, Gradle 8.x. JavaFX 21 is resolved automatically by the desktop module. |
| Android app | JDK 17+, Gradle 8.x, Android SDK 34, build-tools, `ANDROID_HOME` set (or `local.properties` with `sdk.dir`). |
| Backend database | SQLite (bundled driver) for development; PostgreSQL 14+ for production. |

---

## 1. Backend

```bash
cd modules/indra-backend
./gradlew fatJar          # produces build/libs/indra-backend-all.jar
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
./gradlew run
```

Or build a runnable jar that bundles `indra-core`:

```bash
./gradlew jar
java -jar build/libs/indra-desktop-1.1.1.jar
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

One command (checks prerequisites, builds, installs and launches on a connected phone):

```bash
bash scripts/build-and-install-android.sh          # or:  --build  to skip installing
scripts\build-and-install-android.bat             # Windows
```

Release build (you must create your own keystore — none is included):

```bash
keytool -genkeypair -v -keystore indra-release.jks -alias indra -keyalg RSA -keysize 2048 -validity 10000
./gradlew assembleRelease \
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
./gradlew test                         # core unit tests + backend integration tests
./gradlew :indra-core:test             # core only
./gradlew :indra-backend:test          # end-to-end HTTP tests (temp data dir, free port)
```

Android has no instrumented tests in this repository; UI behaviour is exercised manually per
`docs/TESTING.md`.

---

## 5. Continuous integration (no local toolchain needed)

`.github/workflows/android-build.yml` runs on every push and pull request:

1. JDK 17 (Temurin) and the runner's Android SDK 34 / build-tools 34.0.0.
2. `./gradlew :indra-android:assembleDebug` with a warm Gradle cache.
3. The APK is sanity-checked (must contain `classes.dex`, `AndroidManifest.xml` and `res/`) and uploaded as
   the **`indra-ai-debug-apk`** artifact, together with `apk-sha256.txt`.
4. A second, non-blocking job runs the core and backend test suites; its result never hides a buildable app.
   If compilation fails, the relevant Gradle lines are posted back as a commit comment for easy reading.

Download the artifact from **Actions → Android build → latest run**, unzip it, then
`adb install -r indra-android-debug.apk`.

## 6. Packaging

```bash
bash scripts/make-release-zip.sh       # dist/indra-ai-v1.1.1-source.zip
```

The release script excludes build outputs, `node_modules`, IDE folders and any file matching `*.env`,
`*.jks` or `*.keystore`.

## 7. Android release build and signing

```bash
export INDRA_KEYSTORE_PATH=/path/to/indra-release.jks
export INDRA_KEYSTORE_PASSWORD=...     # never commit these
export INDRA_KEY_ALIAS=indra
export INDRA_KEY_PASSWORD=...
./gradlew :indra-android:assembleRelease :indra-android:bundleRelease
# modules/indra-android/build/outputs/apk/release/*.apk
# modules/indra-android/build/outputs/bundle/release/*.aab
```

Without those variables the release build is produced **unsigned**; CI signs it with repository secrets or
with a throw-away key (see `.github/workflows/android-build.yml`). Add the four secrets to get a stable
signing identity across builds.

## 8. The engine on the phone

`IndraApp.onCreate()` starts `LocalBackend`, which builds `BackendConfig.fromMap(...)` (bind `127.0.0.1`,
port `0`, local mode on, random per-install secrets) and runs the same `ApiApplication` used on a PC. The data
directory is `filesDir/engine` and every file in `filesDir/engine/store` is encrypted with the Keystore key.
`ApiClient` resolves the URL at call time (`LocalBackend.awaitReady`), so an activity that starts before the
engine finishes booting simply waits instead of failing.

To debug it from a PC: `adb logcat | grep INDRA` shows `LocalBackend: On-device engine ready at
http://127.0.0.1:<port>`. That port is bound to loopback inside the phone only; use
`adb forward tcp:9999 tcp:<port>` if you need to call it from your machine.
