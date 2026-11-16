# Helper scripts

| Script | Purpose |
|---|---|
| `run-backend.sh` / `.bat` | Build (if needed) and start the backend on port 8420 |
| `run-desktop.sh` / `.bat` | Launch the JavaFX desktop app |
| `start-all.bat` | Windows: backend window + desktop app in one step |
| `build-android.sh` | Build the debug APK and print the output path |
| `build-and-install-android.sh` / `.bat` | Check prerequisites, build the debug APK, install it with `adb` and launch it |
| `make-release-zip.sh` | Produce `dist/indra-ai-v<version>-source.zip` |

All scripts are convenience wrappers around Gradle; read them before running them. They never contain
credentials and never download anything beyond normal Gradle dependencies.
