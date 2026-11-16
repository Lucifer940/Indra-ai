package ai.indra.desktop;

import ai.indra.core.util.Log;
import ai.indra.core.voice.SpeechSanitizer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Windows speech using the built-in .NET speech engine through PowerShell.
 *
 * <p>This is a real implementation that talks to the operating system's own
 * engine - it does not bundle or scrape any third-party service. Windows must
 * have a speech language installed (English is present by default); when it is
 * missing, {@link #isAvailable()} reports that honestly instead of pretending.</p>
 */
public final class DesktopVoice {

    private final DesktopConfig config;

    public DesktopVoice(DesktopConfig config) {
        this.config = config;
    }

    public boolean isSupportedPlatform() {
        return PcControl.isWindows();
    }

    /** Checks once whether the system speech engine can speak. */
    public boolean isAvailable() {
        if (!isSupportedPlatform()) {
            return false;
        }
        return runPowerShell("Add-Type -AssemblyName System.Speech; "
                + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                + "if ($s.GetInstalledVoices().Count -gt 0) { 'yes' } else { 'no' }").contains("yes");
    }

    /**
     * Speaks text with the most natural installed voice, preferring a female
     * voice so the assistant sounds consistent with her character.
     */
    public synchronized boolean speak(String text) {
        String clean = SpeechSanitizer.forSpeech(text);
        if (clean.trim().isEmpty()) {
            return true;
        }
        if (!isSupportedPlatform()) {
            Log.warn("DesktopVoice", "Speech output requires Windows");
            return false;
        }
        List<String> chunks = SpeechSanitizer.chunk(clean, 900);
        boolean ok = true;
        for (String chunk : chunks) {
            ok = ok && speakChunk(chunk);
        }
        return ok;
    }

    private boolean speakChunk(String chunk) {
        String script = "Add-Type -AssemblyName System.Speech; "
                + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                + "$female = $s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Gender -eq 'Female' } | Select-Object -First 1; "
                + "if ($female) { $s.SelectVoice($female.VoiceInfo.Name) }; "
                + "$s.Rate = " + rateForCurrentSpeed() + "; "
                + "$s.Volume = 100; "
                + "$s.Speak([Console]::In.ReadToEnd()); "
                + "$s.Dispose()";
        return runPowerShellScript(script, chunk);
    }

    private int rateForCurrentSpeed() {
        // Windows rate is -10..10; map the natural speed to a small positive offset.
        return "ne".equalsIgnoreCase(config.getLanguage()) ? -1 : 1;
    }

    /**
     * One-shot dictation using the Windows recogniser. Returns null when nothing
     * was understood; callers must surface that honestly to the user.
     */
    public String listenOnce(int seconds) {
        if (!isSupportedPlatform()) {
            return null;
        }
        File output = null;
        try {
            output = File.createTempFile("indra-asr", ".txt");
            output.deleteOnExit();
            String script = "Add-Type -AssemblyName System.Speech; "
                    + "$r = New-Object System.Speech.Recognition.SpeechRecognitionEngine; "
                    + "$r.LoadGrammar((New-Object System.Speech.Recognition.DictationGrammar)); "
                    + "$r.SetInputToDefaultAudioDevice(); "
                    + "$result = $r.Recognize([TimeSpan]::FromSeconds(" + Math.max(3, seconds) + ")); "
                    + "if ($result) { [IO.File]::WriteAllText('" + escape(output.getAbsolutePath()) + "', $result.Text) } "
                    + "$r.Dispose()";
            runPowerShell(script);
            if (output.exists()) {
                String text = new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8).trim();
                return text.isEmpty() ? null : text;
            }
            return null;
        } catch (IOException error) {
            Log.warn("DesktopVoice", "Dictation failed: " + error.getMessage());
            return null;
        } finally {
            if (output != null) {
                output.delete();
            }
        }
    }

    private boolean runPowerShellScript(String script, String stdin) {
        Path scriptFile = null;
        try {
            scriptFile = Files.createTempFile("indra-tts", ".ps1");
            Files.write(scriptFile, script.getBytes(StandardCharsets.UTF_8));
            ProcessBuilder builder = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", scriptFile.toAbsolutePath().toString());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            java.io.OutputStream input = process.getOutputStream();
            input.write(stdin.getBytes(StandardCharsets.UTF_8));
            input.flush();
            input.close();
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.warn("DesktopVoice", "Speech failed: " + error.getMessage());
            return false;
        } finally {
            if (scriptFile != null) {
                try {
                    Files.deleteIfExists(scriptFile);
                } catch (IOException ignored) {
                    // temp file cleanup is best effort
                }
            }
        }
    }

    private String runPowerShell(String script) {
        try {
            ProcessBuilder builder = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-Command", script);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            java.io.InputStream stream = process.getInputStream();
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[512];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            stream.close();
            process.waitFor(20, TimeUnit.SECONDS);
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.debug("DesktopVoice", "PowerShell unavailable: " + error.getMessage());
            return "";
        }
    }

    private static String escape(String value) {
        return value.replace("'", "''");
    }
}
