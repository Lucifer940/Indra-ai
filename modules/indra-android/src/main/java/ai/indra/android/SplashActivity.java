package ai.indra.android;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ai.indra.android.ui.CharacterView;
import ai.indra.android.ui.UiKit;

/** Branded splash with the character, then routes to login or the main app. */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = UiKit.root(this);
        LinearLayout column = UiKit.vertical(this);
        column.setGravity(android.view.Gravity.CENTER);
        column.setPadding(UiKit.dp(this, 24), UiKit.dp(this, 24), UiKit.dp(this, 24), UiKit.dp(this, 24));

        CharacterView character = new CharacterView(this);
        column.addView(character, new LinearLayout.LayoutParams(UiKit.dp(this, 260), UiKit.dp(this, 260)));

        TextView name = UiKit.text(this, "INDRA AI", 30f, UiKit.TEXT, true);
        name.setGravity(android.view.Gravity.CENTER);
        column.addView(name);

        TextView tagline = UiKit.subtitle(this, getString(R.string.tagline));
        tagline.setGravity(android.view.Gravity.CENTER);
        column.addView(tagline);

        TextView credit = UiKit.text(this, "by " + getString(R.string.creator), 12f, UiKit.TEXT_DIM, false);
        credit.setGravity(android.view.Gravity.CENTER);
        credit.setPadding(0, UiKit.dp(this, 18), 0, 0);
        column.addView(credit);

        root.addView(column);
        setContentView(root);
        character.setState(CharacterView.State.HAPPY);

        root.postDelayed(new Runnable() {
            @Override
            public void run() {
                IndraApp app = IndraApp.from(SplashActivity.this);
                Class<?> next = app.prefs().hasSession() ? MainActivity.class : LoginActivity.class;
                startActivity(new Intent(SplashActivity.this, next));
                finish();
            }
        }, 1200L);
    }
}
