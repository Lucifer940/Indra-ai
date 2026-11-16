package ai.indra.android.ui;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import ai.indra.android.MainActivity;

/** Full-screen voice mode: the animated character plus listening controls. */
public final class VoiceScreen extends Screen {

    private final FrameLayout root;
    private final CharacterView character;
    private final TextView status;
    private final TextView transcript;
    private final Button micButton;
    private final Button wakeButton;
    private final Button stopButton;

    public VoiceScreen(MainActivity host) {
        super(host);
        Context context = host;
        root = UiKit.root(context);
        LinearLayout column = UiKit.vertical(context);
        column.setBackgroundColor(UiKit.BG);
        column.setPadding(UiKit.dp(context, 16), UiKit.dp(context, 14), UiKit.dp(context, 16), UiKit.dp(context, 14));

        LinearLayout header = UiKit.horizontal(context);
        header.addView(UiKit.title(context, "Voice mode"));
        column.addView(header);

        character = new CharacterView(context);
        LinearLayout.LayoutParams characterParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        character.setLayoutParams(characterParams);
        column.addView(character);

        status = UiKit.text(context, "Tap the mic and talk to me", 14f, UiKit.TEXT_DIM, false);
        status.setGravity(android.view.Gravity.CENTER);
        status.setPadding(0, UiKit.dp(context, 8), 0, UiKit.dp(context, 8));
        column.addView(status);

        transcript = UiKit.text(context, "", 15f, UiKit.TEXT, false);
        transcript.setGravity(android.view.Gravity.CENTER);
        transcript.setPadding(0, 0, 0, UiKit.dp(context, 10));
        column.addView(transcript);

        LinearLayout controls = UiKit.horizontal(context);
        controls.setGravity(android.view.Gravity.CENTER);

        micButton = UiKit.button(context, "🎙  Talk", true);
        micButton.setContentDescription("Start or stop listening");
        micButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.toggleListening();
            }
        });
        controls.addView(micButton);

        stopButton = UiKit.button(context, "Stop", false);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.stopSpeaking();
            }
        });
        controls.addView(stopButton);

        wakeButton = UiKit.button(context, "Hands-free: off", false);
        wakeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.toggleWakeWord();
                refreshWakeLabel();
            }
        });
        controls.addView(wakeButton);

        LinearLayout toggleRow = UiKit.horizontal(context);
        toggleRow.setGravity(android.view.Gravity.CENTER);
        toggleRow.setPadding(0, UiKit.dp(context, 6), 0, 0);
        Button language = UiKit.smallButton(context, "Language: " + host.prefs().getLanguage().toUpperCase(java.util.Locale.ROOT));
        language.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.toggleLanguage();
                ((Button) v).setText("Language: " + host.prefs().getLanguage().toUpperCase(java.util.Locale.ROOT));
            }
        });
        toggleRow.addView(language);
        column.addView(controls);
        column.addView(toggleRow);

        root.addView(column);
    }

    private void refreshWakeLabel() {
        wakeButton.setText(host.prefs().isWakeWordEnabled() ? "Hands-free: on" : "Hands-free: off");
    }

    @Override
    public View build() {
        refreshWakeLabel();
        return root;
    }

    @Override
    public void onShown() {
        character.start();
    }

    @Override
    public String label() {
        return "Voice";
    }

    public CharacterView character() {
        return character;
    }

    public void setStatus(String text) {
        status.setText(text);
    }

    public void setTranscript(String text) {
        transcript.setText(text);
    }

    public void setMicActive(boolean active) {
        micButton.setText(active ? "◼  Stop" : "🎙  Talk");
    }
}
