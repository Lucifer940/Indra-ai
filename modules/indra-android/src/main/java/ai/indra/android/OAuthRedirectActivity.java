package ai.indra.android;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ai.indra.android.data.ApiClient;
import ai.indra.android.data.Prefs;
import ai.indra.android.data.SessionManager;
import ai.indra.android.ui.UiKit;

/** Receives indraai://oauth2redirect?code=…&state=… and completes the sign-in. */
public class OAuthRedirectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final LinearLayout column = UiKit.screen(this, 24);
        column.setBackgroundColor(UiKit.BG);
        final TextView status = UiKit.bodyText(this,
                "Finishing sign-in… if nothing happens, return to INDRA and try again.");
        column.addView(status);
        setContentView(column);

        Intent intent = getIntent();
        final String code = intent == null ? null : intent.getData() == null ? null
                : intent.getData().getQueryParameter("code");
        String state = intent == null || intent.getData() == null ? null
                : intent.getData().getQueryParameter("state");
        String error = intent == null || intent.getData() == null ? null
                : intent.getData().getQueryParameter("error");
        String provider = intent == null || intent.getData() == null ? "google"
                : intent.getData().getQueryParameter("provider") == null
                ? "google" : intent.getData().getQueryParameter("provider");

        if (error != null) {
            status.setText("Sign-in was cancelled or refused (" + error + ").");
            return;
        }
        if (code == null) {
            status.setText("No authorization code was returned. Starting over…");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        final IndraApp app = IndraApp.from(this);
        final Prefs prefs = app.prefs();
        if (state != null && !state.equals(prefs.getOauthState())) {
            status.setText("The sign-in response could not be verified. Please try again.");
            prefs.clearOauth();
            return;
        }
        final ApiClient api = app.api();
        final SessionManager session = app.session();
        final String verifier = prefs.getOauthVerifier();
        final String providerName = provider;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    session.oauth(providerName, code, BuildConfig.OAUTH_REDIRECT, verifier);
                    prefs.clearOauth();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            startActivity(new Intent(OAuthRedirectActivity.this, MainActivity.class));
                            finish();
                        }
                    });
                } catch (final ApiClient.ApiException failure) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            status.setText("Sign-in failed: " + failure.getMessage()
                                    + "\n\nYou can still sign in with email inside the app.");
                            startActivity(new Intent(OAuthRedirectActivity.this, LoginActivity.class));
                            finish();
                        }
                    });
                }
            }
        }).start();
    }
}
