package ai.indra.android.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ai.indra.android.BuildConfig;
import ai.indra.android.MainActivity;
import ai.indra.android.R;
import ai.indra.android.data.ApiClient;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.BrainProfile;
import ai.indra.core.model.Persona;
import ai.indra.core.util.Strings;

import java.util.Locale;

/** All user-facing settings: persona, brain, language, voice, keys, devices, licence, account. */
public final class SettingsScreen extends Screen {

    private final ScrollView root;
    private final LinearLayout column;

    public SettingsScreen(MainActivity host) {
        super(host);
        Context context = host;
        column = UiKit.screen(context, 16);
        column.setBackgroundColor(UiKit.BG);
        root = UiKit.scroll(context, column);
    }

    @Override
    public View build() {
        column.removeAllViews();
        Context context = host;
        column.addView(UiKit.title(context, "Settings"));
        column.addView(UiKit.subtitle(context, "INDRA AI v" + BuildConfig.VERSION_NAME + " · by Umesh Chaudhary"));

        // Persona
        column.addView(UiKit.sectionLabel(context, "Personality"));
        column.addView(valueRow("Assistant mode", Persona.fromId(host.prefs().getPersona()).getLabel(),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        choosePersona();
                    }
                }));
        column.addView(valueRow("Brain", BrainProfile.fromId(host.prefs().getBrain()).getLabel(),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        chooseBrain();
                    }
                }));
        column.addView(valueRow("Language", "English".equals(host.prefs().getLanguage()) ? "English" : "नेपाली",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        host.toggleLanguage();
                        host.reloadSettings();
                    }
                }));

        // AI provider
        column.addView(UiKit.sectionLabel(context, "AI provider"));
        column.addView(valueRow("Provider / model", host.prefs().getProvider() + " · " + host.prefs().getModel(),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        chooseProvider();
                    }
                }));
        column.addView(valueRow("API keys", "Manage keys, add or reset", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showKeysDialog();
            }
        }));
        column.addView(UiKit.subtitle(context, "Keys are stored encrypted on the server and never placed in the app. "
                + "Add publisher keys (OpenAI, DeepSeek, OpenRouter…) in Settings → API keys."));

        // Voice
        column.addView(UiKit.sectionLabel(context, "Voice"));
        column.addView(UiKit.toggleRow(context, "Speak replies aloud",
                "Uses the device's natural text-to-speech voice", host.prefs().isAutoSpeak(),
                new UiKit.ToggleListener() {
                    @Override
                    public void onChanged(boolean checked) {
                        save(host.prefs().getPersona(), host.prefs().getBrain(), host.prefs().getLanguage(),
                                host.prefs().getProvider(), host.prefs().getModel(), host.prefs().isMemoryEnabled(),
                                host.prefs().isAutoMemory(), checked, host.prefs().getSpeechRate(),
                                host.prefs().getSpeechPitch());
                    }
                }));
        column.addView(valueRow("Speech rate", String.format(Locale.US, "%.2fx", host.prefs().getSpeechRate()),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        chooseRate();
                    }
                }));
        column.addView(valueRow("Hands-free wake word",
                (host.prefs().isWakeWordEnabled() ? "On" : "Off") + " · say \"" + host.prefs().getWakeWord() + "\"",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        host.toggleWakeWord();
                        host.reloadSettings();
                    }
                }));
        column.addView(valueRow("Wake word", host.prefs().getWakeWord(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editText("Wake word", host.prefs().getWakeWord(), new TextCallback() {
                    @Override
                    public void onText(String value) {
                        if (!value.trim().isEmpty()) {
                            host.prefs().setWakeWord(value.trim());
                            host.session().pushSettings();
                            host.reloadSettings();
                        }
                    }
                });
            }
        }));

        // Memory
        column.addView(UiKit.sectionLabel(context, "Memory & privacy"));
        column.addView(valueRow("Saved memories", "Open memory screen", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openTab(2);
            }
        }));
        column.addView(valueRow("Clear conversation history", "Deletes messages on this device and the server",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        confirmClearHistory();
                    }
                }));
        column.addView(valueRow("Export my data", "JSON file you can save or share", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.exportData();
            }
        }));

        // Devices
        column.addView(UiKit.sectionLabel(context, "Devices & sync"));
        column.addView(valueRow("Connected devices", "Pair with Windows, revoke access",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showDevicesDialog();
                    }
                }));
        column.addView(valueRow("Sync now", "Pull memories, tasks and settings", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.syncNow();
            }
        }));

        // Licence
        column.addView(UiKit.sectionLabel(context, "Licence"));
        column.addView(valueRow("Licence key", host.prefs().getLicenseKey().isEmpty()
                        ? "Not activated" : mask(host.prefs().getLicenseKey()),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showLicenseDialog();
                    }
                }));

        // Tools
        column.addView(UiKit.sectionLabel(context, "Tools"));
        column.addView(valueRow("Whiteboard", "Draw, sketch and save", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.showWhiteboard();
            }
        }));
        column.addView(valueRow("Conversation history", "Browse past chats", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.showHistory();
            }
        }));

        // Engine / Connection
        column.addView(UiKit.sectionLabel(context, "Engine"));
        final ai.indra.android.local.LocalBackend engine =
                ai.indra.android.local.LocalBackend.get(context);
        column.addView(UiKit.subtitle(context, engine.describe()
                + " · data encrypted with the Android Keystore, loopback only"));
        column.addView(valueRow("Use on-device engine",
                host.prefs().useEmbeddedBackend() ? "On (recommended)" : "Off - using a remote server",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        host.prefs().setUseEmbeddedBackend(true);
                        host.toast("INDRA is using the engine inside this phone");
                        host.openTab(4);
                    }
                }));
        column.addView(valueRow("Server", host.prefs().getBaseUrl().isEmpty()
                        ? "not set (on-device engine)" : host.prefs().getBaseUrl(),
                new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editText("Server URL", host.prefs().getBaseUrl(), new TextCallback() {
                    @Override
                    public void onText(String value) {
                        if (value.startsWith("http")) {
                            host.prefs().setBaseUrl(value.trim());
                            host.api().setBaseUrl(value.trim());
                            host.toast("Server updated");
                        } else {
                            host.toast("Enter a full URL starting with http:// or https://");
                        }
                    }
                });
            }
        }));
        column.addView(UiKit.subtitle(context, "Nothing has to be configured: the app ships with its own "
                + "backend. Set a server only to share one account with your PC "
                + "(for example http://192.168.1.20:8420) or a hosted HTTPS domain."));

        // Account
        column.addView(UiKit.sectionLabel(context, "Account"));
        JsonObject user = host.session().user();
        column.addView(valueRow("Signed in as", user == null ? "unknown" : user.getString("email", "unknown"),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        host.toast("Profile: " + (host.session().user() == null ? "-"
                                : host.session().user().getString("email", "-")));
                    }
                }));
        if (host.session().isAdmin()) {
            column.addView(valueRow("Admin console", "Users, config, licences, audit log",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            host.openAdmin();
                        }
                    }));
        }
        column.addView(valueRow("Sign out", "Ends this session", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.signOut();
            }
        }));

        column.addView(UiKit.sectionLabel(context, "About"));
        column.addView(UiKit.bodyText(context, "INDRA AI " + BuildConfig.VERSION_NAME
                + "\nCreated by Umesh Chaudhary" + "\nBuilt with Java, a shared core and a secure backend."));
        column.addView(UiKit.bodyText(context, host.getString(R.string.about_legal)));
        return root;
    }

    @Override
    public String label() {
        return "Settings";
    }

    private void save(String persona, String brain, String language, String provider, String model,
                      boolean memory, boolean autoMemory, boolean autoSpeak, float rate, float pitch) {
        host.prefs().setLocalSettings(persona, brain, language, host.prefs().getTheme(), provider, model,
                memory, autoMemory, autoSpeak, rate, pitch);
        host.session().pushSettings();
        host.reloadSettings();
    }

    private LinearLayout valueRow(String label, String value, View.OnClickListener click) {
        Context context = host;
        LinearLayout row = UiKit.card(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.addView(UiKit.text(context, label, 15f, UiKit.TEXT, false));
        TextView valueView = UiKit.text(context, value, 13f, UiKit.ACCENT, false);
        valueView.setPadding(0, UiKit.dp(context, 3), 0, 0);
        row.addView(valueView);
        row.setOnClickListener(click);
        row.setClickable(true);
        row.setFocusable(true);
        return row;
    }

    private void choosePersona() {
        final Persona[] values = Persona.values();
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i].getLabel() + " - " + values[i].getDescription();
        }
        new AlertDialog.Builder(host).setTitle("Assistant mode")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        save(values[which].getId(), host.prefs().getBrain(), host.prefs().getLanguage(),
                                host.prefs().getProvider(), host.prefs().getModel(), host.prefs().isMemoryEnabled(),
                                host.prefs().isAutoMemory(), host.prefs().isAutoSpeak(),
                                host.prefs().getSpeechRate(), host.prefs().getSpeechPitch());
                    }
                }).show();
    }

    private void chooseBrain() {
        final BrainProfile[] values = BrainProfile.values();
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i].getLabel() + " - " + values[i].getDescription();
        }
        new AlertDialog.Builder(host).setTitle("Choose a brain")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        save(host.prefs().getPersona(), values[which].getId(), host.prefs().getLanguage(),
                                host.prefs().getProvider(), host.prefs().getModel(), host.prefs().isMemoryEnabled(),
                                host.prefs().isAutoMemory(), host.prefs().isAutoSpeak(),
                                host.prefs().getSpeechRate(), host.prefs().getSpeechPitch());
                    }
                }).show();
    }

    private void chooseProvider() {
        host.executor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final JsonObject catalogue = host.api().settingsCatalogue();
                    host.runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            showProviderPicker(catalogue);
                        }
                    });
                } catch (final ApiClient.ApiException error) {
                    host.toastOnUi("Could not load providers: " + error.getMessage());
                }
            }
        });
    }

    private void showProviderPicker(JsonObject catalogue) {
        JsonArray providers = catalogue.getArray("providers");
        if (providers == null || providers.size() == 0) {
            host.toast("No provider catalogue available");
            return;
        }
        final String[] ids = new String[providers.size()];
        final String[] labels = new String[providers.size()];
        for (int i = 0; i < providers.size(); i++) {
            JsonObject provider = providers.getObject(i);
            ids[i] = provider.getString("id", "");
            labels[i] = provider.getString("label", ids[i]) + " - " + provider.getString("model", "");
        }
        new AlertDialog.Builder(host).setTitle("AI provider")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String model = "";
                        for (int i = 0; i < catalogue.getArray("providers").size(); i++) {
                            JsonObject provider = catalogue.getArray("providers").getObject(i);
                            if (provider.getString("id", "").equals(ids[which])) {
                                model = provider.getString("model", "");
                            }
                        }
                        save(host.prefs().getPersona(), host.prefs().getBrain(), host.prefs().getLanguage(),
                                ids[which], model, host.prefs().isMemoryEnabled(),
                                host.prefs().isAutoMemory(), host.prefs().isAutoSpeak(),
                                host.prefs().getSpeechRate(), host.prefs().getSpeechPitch());
                    }
                }).show();
    }

    private void chooseRate() {
        final String[] labels = {"Slow (0.85x)", "Natural (1.0x)", "Brisk (1.15x)", "Fast (1.3x)"};
        final float[] rates = {0.85f, 1.0f, 1.15f, 1.3f};
        new AlertDialog.Builder(host).setTitle("Speech rate")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        save(host.prefs().getPersona(), host.prefs().getBrain(), host.prefs().getLanguage(),
                                host.prefs().getProvider(), host.prefs().getModel(), host.prefs().isMemoryEnabled(),
                                host.prefs().isAutoMemory(), host.prefs().isAutoSpeak(), rates[which],
                                host.prefs().getSpeechPitch());
                    }
                }).show();
    }

    private void showKeysDialog() {
        host.executor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final JsonObject response = host.api().keys();
                    host.runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            renderKeys(response);
                        }
                    });
                } catch (final ApiClient.ApiException error) {
                    host.toastOnUi("Could not load keys: " + error.getMessage());
                }
            }
        });
    }

    private void renderKeys(JsonObject response) {
        Context context = host;
        LinearLayout sheet = UiKit.vertical(context);
        sheet.setPadding(UiKit.dp(context, 16), UiKit.dp(context, 8), UiKit.dp(context, 16), 0);
        JsonArray keys = response.getArray("keys");
        if (keys == null || keys.size() == 0) {
            sheet.addView(UiKit.subtitle(context, "No keys yet. Add a publisher key for the provider you want to use."));
        } else {
            for (int i = 0; i < keys.size(); i++) {
                JsonObject key = keys.getObject(i);
                LinearLayout row = UiKit.horizontal(context);
                LinearLayout texts = UiKit.vertical(context);
                texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                texts.addView(UiKit.text(context, key.getString("provider", "?") + " · "
                        + key.getString("label", ""), 14f, UiKit.TEXT, false));
                texts.addView(UiKit.text(context, key.getString("masked", "••••") + " · "
                        + (key.getBoolean("active", true) ? "active" : "disabled"), 11f, UiKit.TEXT_DIM, false));
                row.addView(texts);
                Button remove = UiKit.smallButton(context, "Remove");
                final String id = key.getString("id", "");
                remove.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        host.removeApiKey(id);
                    }
                });
                row.addView(remove);
                sheet.addView(row);
            }
        }
        JsonArray providers = response.getArray("providers");
        if (providers != null && providers.size() > 0) {
            sheet.addView(UiKit.sectionLabel(context, "Add a key"));
            LinearLayout wrap = UiKit.horizontal(context);
            for (int i = 0; i < providers.size() && i < 6; i++) {
                JsonObject provider = providers.getObject(i);
                Button add = UiKit.smallButton(context, provider.getString("label", provider.getString("id", "?")));
                final String providerId = provider.getString("id", "");
                add.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        promptForKey(providerId);
                    }
                });
                wrap.addView(add);
            }
            sheet.addView(wrap);
        }
        new AlertDialog.Builder(context)
                .setTitle("API keys")
                .setView(UiKit.scroll(context, sheet))
                .setPositiveButton("Reset all", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        confirmResetKeys();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void promptForKey(final String provider) {
        Context context = host;
        LinearLayout form = UiKit.vertical(context);
        form.setPadding(UiKit.dp(context, 16), UiKit.dp(context, 8), UiKit.dp(context, 16), 0);
        final EditText label = UiKit.singleLine(context, "Label (e.g. Personal)");
        final EditText key = UiKit.password(context, "Paste the key");
        form.addView(label);
        form.addView(key);
        form.addView(UiKit.subtitle(context, "The key is sent over HTTPS and stored encrypted on the server. "
                + "It is shown masked from then on and is never embedded in the app."));
        new AlertDialog.Builder(context)
                .setTitle("Add " + provider + " key")
                .setView(form)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        host.addApiKey(provider, label.getText().toString().trim(),
                                key.getText().toString().trim(), true);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmResetKeys() {
        new AlertDialog.Builder(host)
                .setTitle("Reset keys?")
                .setMessage("Removes every stored API key. Conversations fall back to the offline brain until you add a new key.")
                .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        host.resetApiKeys();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showDevicesDialog() {
        host.executor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final JsonObject response = host.api().devices();
                    host.runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            renderDevices(response);
                        }
                    });
                } catch (final ApiClient.ApiException error) {
                    host.toastOnUi("Could not load devices: " + error.getMessage());
                }
            }
        });
    }

    private void renderDevices(JsonObject response) {
        Context context = host;
        LinearLayout sheet = UiKit.vertical(context);
        sheet.setPadding(UiKit.dp(context, 16), UiKit.dp(context, 8), UiKit.dp(context, 16), 0);
        JsonArray devices = response.getArray("devices");
        if (devices == null || devices.size() == 0) {
            sheet.addView(UiKit.subtitle(context, "No devices paired yet."));
        } else {
            for (int i = 0; i < devices.size(); i++) {
                final JsonObject device = devices.getObject(i);
                LinearLayout row = UiKit.horizontal(context);
                LinearLayout texts = UiKit.vertical(context);
                texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                texts.addView(UiKit.text(context, device.getString("name", "Device"), 14f, UiKit.TEXT, false));
                texts.addView(UiKit.text(context, device.getString("platform", "") + " · "
                        + (device.getBoolean("trusted", true) ? "trusted" : "revoked"), 11f, UiKit.TEXT_DIM, false));
                row.addView(texts);
                Button revoke = UiKit.smallButton(context, "Revoke");
                revoke.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        host.revokeDevice(device.getString("id", ""));
                    }
                });
                row.addView(revoke);
                sheet.addView(row);
            }
        }
        new AlertDialog.Builder(context).setTitle("Connected devices").setView(sheet)
                .setPositiveButton("Pair new device", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        host.startPairing();
                    }
                })
                .setNegativeButton("Close", null).show();
    }

    private void showLicenseDialog() {
        Context context = host;
        final EditText input = UiKit.singleLine(context, "INDRA-XXXX-XXXX-XXXX");
        input.setText(host.prefs().getLicenseKey());
        new AlertDialog.Builder(context)
                .setTitle("Licence key")
                .setMessage("Enter the key your administrator gave you. It is remembered on this device.")
                .setView(input)
                .setPositiveButton("Activate", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        host.activateLicense(input.getText().toString().trim());
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(host)
                .setTitle("Clear history?")
                .setMessage("This deletes stored conversations from this phone and your account.")
                .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        host.clearHistory();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void editText(String title, String initial, final TextCallback callback) {
        Context context = host;
        final EditText input = UiKit.input(context, title, InputType.TYPE_CLASS_TEXT);
        input.setText(initial);
        new AlertDialog.Builder(context).setTitle(title).setView(input)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callback.onText(input.getText().toString());
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private String mask(String key) {
        if (key == null || key.length() < 6) {
            return "—";
        }
        return Strings.maskSecret(key);
    }

    /** Callback for simple text dialogs. */
    private interface TextCallback {
        void onText(String value);
    }
}
