package ai.indra.android;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ai.indra.android.data.ApiClient;
import ai.indra.android.local.LocalBackend;
import ai.indra.android.data.Prefs;
import ai.indra.android.data.SessionManager;
import ai.indra.android.ui.CharacterView;
import ai.indra.android.ui.UiKit;
import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Log;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Sign in / register, plus Google and GitHub OAuth (PKCE, browser-based) and the
 * server address field. No credentials are ever stored in the APK.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText email;
    private EditText password;
    private EditText displayName;
    private TextView error;
    private Button primary;
    private boolean registerMode;

    private Prefs prefs;
    private ApiClient api;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IndraApp app = IndraApp.from(this);
        prefs = app.prefs();
        api = app.api();
        session = app.session();
        if (prefs.hasSession()) {
            goToMain();
            return;
        }
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(UiKit.BG);
        LinearLayout column = UiKit.screen(this, 22);
        column.setBackgroundColor(UiKit.BG);

        CharacterView character = new CharacterView(this);
        column.addView(character, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                UiKit.dp(this, 200)));

        TextView title = UiKit.title(this, getString(R.string.login_title));
        title.setGravity(android.view.Gravity.CENTER);
        column.addView(title);
        TextView subtitle = UiKit.subtitle(this, getString(R.string.login_subtitle));
        subtitle.setGravity(android.view.Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, UiKit.dp(this, 16));
        column.addView(subtitle);

        Button skip = UiKit.button(this, "Continue without signing in  →", true);
        skip.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLocally((Button) v);
            }
        });
        column.addView(skip);

        TextView skipHint = UiKit.text(this,
                "INDRA runs on this phone. No PC, no account and no internet are needed for chat, "
                        + "memory, tasks and reminders. Sign in only if you want sync or the licence.",
                12f, UiKit.TEXT_DIM, false);
        skipHint.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 14));
        column.addView(skipHint);

        Button licence = UiKit.button(this, "I have a licence key", false);
        licence.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        licence.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLicenceDialog();
            }
        });
        column.addView(licence);

        column.addView(UiKit.subtitle(this, "Optional: sign in or create an account"));

        displayName = UiKit.singleLine(this, getString(R.string.display_name));
        column.addView(displayName);
        email = UiKit.input(this, getString(R.string.email),
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setSingleLine(true);
        column.addView(email);
        password = UiKit.password(this, getString(R.string.password));
        column.addView(password);

        error = UiKit.text(this, "", 13f, UiKit.ERROR, false);
        error.setPadding(0, UiKit.dp(this, 4), 0, UiKit.dp(this, 8));
        column.addView(error);

        primary = UiKit.button(this, getString(R.string.sign_in), true);
        primary.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        primary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
        column.addView(primary);

        Button switchMode = UiKit.button(this, getString(R.string.create_account), false);
        switchMode.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        switchMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerMode = !registerMode;
                displayName.setVisibility(registerMode ? View.VISIBLE : View.GONE);
                primary.setText(registerMode ? getString(R.string.create_account) : getString(R.string.sign_in));
                ((Button) v).setText(registerMode ? getString(R.string.sign_in) : getString(R.string.create_account));
            }
        });
        column.addView(switchMode);

        column.addView(UiKit.divider(this));

        Button google = UiKit.button(this, getString(R.string.continue_google), false);
        google.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        google.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startOauth("google");
            }
        });
        column.addView(google);

        Button github = UiKit.button(this, getString(R.string.continue_github), false);
        github.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        github.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startOauth("github");
            }
        });
        column.addView(github);

        column.addView(UiKit.subtitle(this, "Google and GitHub sign-in open your browser and return here. "
                + "They need the provider client IDs configured on the server - see config/backend.env.example."));

        column.addView(UiKit.divider(this));
        final EditText server = UiKit.singleLine(this, "Server URL");
        server.setText(prefs.getBaseUrl());
        column.addView(server);
        Button saveServer = UiKit.smallButton(this, "Save server address");
        saveServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = server.getText().toString().trim();
                if (!url.startsWith("http")) {
                    error.setText("Server URL must start with http:// or https://");
                    return;
                }
                prefs.setBaseUrl(url);
                api.setBaseUrl(url);
                error.setText("");
            }
        });
        column.addView(saveServer);
        column.addView(UiKit.subtitle(this, "Leave this empty to use the engine inside the phone. "
                + "Set it only to share one account with your PC, for example http://192.168.1.20:8420, "
                + "or a hosted HTTPS domain."));
        final TextView engineState = UiKit.text(this, engineSummary(), 12f, UiKit.TEXT_DIM, false);
        engineState.setPadding(0, UiKit.dp(this, 4), 0, 0);
        column.addView(engineState);

        TextView credit = UiKit.text(this, "INDRA AI · created by " + getString(R.string.creator),
                12f, UiKit.TEXT_DIM, false);
        credit.setGravity(android.view.Gravity.CENTER);
        credit.setPadding(0, UiKit.dp(this, 18), 0, 0);
        column.addView(credit);

        scroll.addView(column);
        setContentView(scroll);
    }

    /** One tap path: no account, no server, straight into the app. */
    private void startLocally(final Button button) {
        button.setEnabled(false);
        button.setText("Starting INDRA…");
        error.setText(engineSummary());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    session.continueLocally();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            goToMain();
                        }
                    });
                } catch (final ApiClient.ApiException failure) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            button.setEnabled(true);
                            button.setText("Continue without signing in  →");
                            error.setText(friendly(failure));
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Activates a licence without forcing a sign-up: the on-device session is
     * created first (if needed), then the key is validated by the engine and kept
     * encrypted on the device.
     */
    private void showLicenceDialog() {
        final EditText input = UiKit.singleLine(this, "INDRA-XXXX-XXXX-XXXX");
        input.setText(prefs.getLicenseKey());
        new AlertDialog.Builder(this)
                .setTitle("Licence key")
                .setMessage("Enter the key you were given. It is stored encrypted on this device.")
                .setView(input)
                .setPositiveButton("Activate", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activateLicence(input.getText().toString().trim());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void activateLicence(final String key) {
        if (key.isEmpty()) {
            error.setText("Enter a licence key first");
            return;
        }
        error.setText("Checking the licence…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!session.isSignedIn()) {
                        session.continueLocally();
                    }
                    final JsonObject result = api.activateLicense(key);
                    prefs.setLicenseKey(key);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String message = result.getString("message", "Licence activated");
                            android.widget.Toast.makeText(LoginActivity.this, message,
                                    android.widget.Toast.LENGTH_LONG).show();
                            goToMain();
                        }
                    });
                } catch (final ApiClient.ApiException failure) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            error.setText(friendly(failure));
                        }
                    });
                }
            }
        }).start();
    }

    private String engineSummary() {
        LocalBackend engine = LocalBackend.get(this);
        if (engine.baseUrl() != null) {
            return "On-device engine ready (port " + engine.port() + ")";
        }
        String failure = engine.failure();
        return failure == null ? "Starting the on-device engine…" : "On-device engine: " + failure;
    }

    private void submit() {
        String mail = email.getText().toString().trim();
        String pass = password.getText().toString();
        String name = displayName.getText().toString().trim();
        if (mail.isEmpty() || pass.isEmpty()) {
            error.setText("Enter your email and password");
            return;
        }
        if (pass.length() < 8) {
            error.setText("Passwords need at least 8 characters");
            return;
        }
        error.setText("");
        primary.setEnabled(false);
        primary.setText("Please wait…");
        final boolean creating = registerMode;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (creating) {
                        session.register(email.getText().toString().trim(), password.getText().toString(),
                                displayName.getText().toString().trim());
                    } else {
                        session.login(email.getText().toString().trim(), password.getText().toString());
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            goToMain();
                        }
                    });
                } catch (final ApiClient.ApiException failure) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            primary.setEnabled(true);
                            primary.setText(registerMode ? getString(R.string.create_account) : getString(R.string.sign_in));
                            error.setText(friendly(failure));
                        }
                    });
                }
            }
        }).start();
    }

    private String friendly(ApiClient.ApiException failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if (failure.getStatus() == 401) {
            return "That email or password doesn't match our records";
        }
        if (failure.getStatus() == 409) {
            return "An account with that email already exists - sign in instead";
        }
        if (failure.getStatus() == 0) {
            String engine = LocalBackend.get(this).describe();
            return api.isLocal()
                    ? "The on-device engine is not responding (" + engine + "). Restart the app to try again."
                    : "Cannot reach the server at " + prefs.getBaseUrl()
                    + ". Check the address and that the backend is running.";
        }
        if (failure.getStatus() == 429) {
            return "Too many attempts - please wait a minute and try again";
        }
        return message.isEmpty() ? getString(R.string.error_generic) : message;
    }

    /** Starts the PKCE browser flow. */
    private void startOauth(final String provider) {
        error.setText("Opening " + provider + " sign-in…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String verifier = randomVerifier();
                    String challenge = challengeFor(verifier);
                    String state = UUID.randomUUID().toString();
                    prefs.setOauth(state, verifier);
                    JsonObject response = api.oauthStart(provider, state, BuildConfig.OAUTH_REDIRECT, challenge);
                    final String url = response.getString("authorizeUrl",
                            response.getString("url", ""));
                    if (url.isEmpty()) {
                        throw new ApiClient.ApiException(0, "OAUTH_NOT_CONFIGURED",
                                provider + " sign-in is not configured on the server yet", null);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            error.setText("");
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        }
                    });
                } catch (final ApiClient.ApiException failure) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            error.setText(friendly(failure));
                        }
                    });
                }
            }
        }).start();
    }

    private static String randomVerifier() {
        byte[] bytes = new byte[40];
        new SecureRandom().nextBytes(bytes);
        return android.util.Base64.encodeToString(bytes,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
    }

    private static String challengeFor(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes("UTF-8"));
            return android.util.Base64.encodeToString(hash,
                    android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
        } catch (Exception error) {
            Log.error("LoginActivity", "PKCE digest failed", error);
            return verifier;
        }
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
