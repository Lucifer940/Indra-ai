package ai.indra.android;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ai.indra.android.data.ApiClient;
import ai.indra.android.data.SessionManager;
import ai.indra.android.ui.UiKit;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Log;

/**
 * Admin console. Every action is authorised by the backend; the app merely
 * renders what the ADMIN role is allowed to see. Nothing here is reachable for a
 * normal account, and no credential is ever displayed.
 */
public class AdminActivity extends AppCompatActivity {

    private LinearLayout column;
    private ApiClient api;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IndraApp app = IndraApp.from(this);
        api = app.api();
        session = app.session();
        if (!session.isAdmin()) {
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(UiKit.BG);
        column = UiKit.screen(this, 16);
        column.setBackgroundColor(UiKit.BG);
        scroll.addView(column);
        setContentView(scroll);

        column.addView(UiKit.title(this, "Admin console"));
        column.addView(UiKit.subtitle(this, "Server-validated administrator tools. Changes are audit-logged."));
        loadStats();
        loadUsers();
        loadLicenses();
        loadConfig();
    }

    private void loadStats() {
        run(new Call() {
            @Override
            public JsonObject call() throws ApiClient.ApiException {
                return api.adminStats();
            }
        }, new Callback() {
            @Override
            public void onResult(JsonObject result) {
                LinearLayout card = UiKit.card(AdminActivity.this);
                card.addView(UiKit.text(AdminActivity.this, "Overview", 15f, UiKit.TEXT, true));
                card.addView(UiKit.bodyText(AdminActivity.this, "Users: " + result.getInt("users", 0)
                        + "\nMessages: " + result.getInt("messages", 0)
                        + "\nMemories: " + result.getInt("memories", 0)
                        + "\nTasks: " + result.getInt("tasks", 0)
                        + "\nDevices: " + result.getInt("devices", 0)
                        + "\nLicences: " + result.getInt("licenses", 0)));
                column.addView(card);
            }
        });
    }

    private void loadUsers() {
        run(new Call() {
            @Override
            public JsonObject call() throws ApiClient.ApiException {
                return api.adminUsers(null);
            }
        }, new Callback() {
            @Override
            public void onResult(JsonObject result) {
                JsonArray users = result.getArrayOrEmpty("users");
                LinearLayout card = UiKit.card(AdminActivity.this);
                card.addView(UiKit.text(AdminActivity.this, "Users (" + users.size() + ")", 15f, UiKit.TEXT, true));
                for (int i = 0; i < users.size() && i < 12; i++) {
                    final JsonObject user = users.getObject(i);
                    LinearLayout row = UiKit.horizontal(AdminActivity.this);
                    TextView label = UiKit.text(AdminActivity.this, user.getString("email", "?") + " · "
                            + user.getString("role", "USER"), 13f, UiKit.TEXT, false);
                    label.setLayoutParams(new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    row.addView(label);
                    Button toggle = UiKit.smallButton(AdminActivity.this, "Role");
                    toggle.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            changeRole(user);
                        }
                    });
                    row.addView(toggle);
                    card.addView(row);
                }
                card.addView(UiKit.subtitle(AdminActivity.this, "Tap Role to switch between USER and ADMIN."));
                column.addView(card);
            }
        });
    }

    private void changeRole(final JsonObject user) {
        final String next = "ADMIN".equals(user.getString("role", "USER")) ? "USER" : "ADMIN";
        new AlertDialog.Builder(this)
                .setTitle("Change role")
                .setMessage("Set " + user.getString("email", "") + " to " + next + "?")
                .setPositiveButton("Change", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        run(new Call() {
                            @Override
                            public JsonObject call() throws ApiClient.ApiException {
                                return api.adminUpdateUser(user.getString("id", ""),
                                        new JsonObject().put("role", next));
                            }
                        }, new Callback() {
                            @Override
                            public void onResult(JsonObject result) {
                                toast("Role updated to " + next);
                                recreate();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void loadLicenses() {
        run(new Call() {
            @Override
            public JsonObject call() throws ApiClient.ApiException {
                return api.adminLicenses();
            }
        }, new Callback() {
            @Override
            public void onResult(JsonObject result) {
                JsonArray licenses = result.getArrayOrEmpty("licenses");
                LinearLayout card = UiKit.card(AdminActivity.this);
                card.addView(UiKit.text(AdminActivity.this, "Licences (" + licenses.size() + ")",
                        15f, UiKit.TEXT, true));
                for (int i = 0; i < licenses.size() && i < 10; i++) {
                    JsonObject license = licenses.getObject(i);
                    card.addView(UiKit.bodyText(AdminActivity.this, license.getString("key", "?") + " · "
                            + license.getString("plan", "") + " · "
                            + (license.getBoolean("revoked", false) ? "revoked"
                            : license.getString("status", "active"))));
                }
                Button issue = UiKit.button(AdminActivity.this, "Issue licence code", true);
                issue.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        promptIssueLicense();
                    }
                });
                card.addView(issue);
                column.addView(card);
            }
        });
    }

    private void promptIssueLicense() {
        final EditText plan = UiKit.singleLine(this, "Plan (personal, family, pro)");
        final EditText count = UiKit.singleLine(this, "How many codes?");
        count.setText("1");
        LinearLayout form = UiKit.vertical(this);
        form.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 8), UiKit.dp(this, 16), 0);
        form.addView(plan);
        form.addView(count);
        new AlertDialog.Builder(this).setTitle("Issue licence").setView(form)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        run(new Call() {
                            @Override
                            public JsonObject call() throws ApiClient.ApiException {
                                return api.adminIssueLicense(plan.getText().toString().trim(), Integer.parseInt(
                                        count.getText().toString().trim().isEmpty() ? "1"
                                                : count.getText().toString().trim()));
                            }
                        }, new Callback() {
                            @Override
                            public void onResult(JsonObject result) {
                                JsonArray keys = result.getArrayOrEmpty("keys");
                                StringBuilder text = new StringBuilder();
                                for (int i = 0; i < keys.size(); i++) {
                                    text.append(keys.getString(i, "")).append('\n');
                                }
                                new AlertDialog.Builder(AdminActivity.this)
                                        .setTitle("New licence codes")
                                        .setMessage(text.length() == 0 ? "No codes returned" : text.toString())
                                        .setPositiveButton("Done", null).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void loadConfig() {
        run(new Call() {
            @Override
            public JsonObject call() throws ApiClient.ApiException {
                return api.adminConfig();
            }
        }, new Callback() {
            @Override
            public void onResult(JsonObject result) {
                final JsonObject config = result.getObjectOrEmpty("config");
                LinearLayout card = UiKit.card(AdminActivity.this);
                card.addView(UiKit.text(AdminActivity.this, "Server config", 15f, UiKit.TEXT, true));
                card.addView(UiKit.bodyText(AdminActivity.this, config.toPrettyJson()));
                card.addView(UiKit.subtitle(AdminActivity.this, "Pushed config reaches every client on its next "
                        + "refresh. Keep values conservative - they apply to all users."));
                Button edit = UiKit.button(AdminActivity.this, "Edit config", false);
                edit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final EditText input = UiKit.input(AdminActivity.this, "Config JSON",
                                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                        input.setText(config.toPrettyJson());
                        new AlertDialog.Builder(AdminActivity.this).setTitle("Server config").setView(input)
                                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            final JsonObject parsed = JsonObject.parse(input.getText().toString());
                                            run(new Call() {
                                                @Override
                                                public JsonObject call() throws ApiClient.ApiException {
                                                    return api.adminSaveConfig(parsed);
                                                }
                                            }, new Callback() {
                                                @Override
                                                public void onResult(JsonObject saved) {
                                                    toast("Config updated");
                                                    recreate();
                                                }
                                            });
                                        } catch (RuntimeException error) {
                                            toast("That isn't valid JSON: " + error.getMessage());
                                        }
                                    }
                                })
                                .setNegativeButton("Cancel", null).show();
                    }
                });
                card.addView(edit);
                column.addView(card);

                Button audit = UiKit.button(AdminActivity.this, "View audit log", false);
                audit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showAudit();
                    }
                });
                column.addView(audit);
            }
        });
    }

    private void showAudit() {
        run(new Call() {
            @Override
            public JsonObject call() throws ApiClient.ApiException {
                return api.adminAudit();
            }
        }, new Callback() {
            @Override
            public void onResult(JsonObject result) {
                JsonArray entries = result.getArrayOrEmpty("events");
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < entries.size() && i < 40; i++) {
                    JsonObject event = entries.getObject(i);
                    text.append(event.getString("action", "?"))
                            .append(" · ").append(event.getString("actorEmail", ""))
                            .append('\n');
                }
                new AlertDialog.Builder(AdminActivity.this)
                        .setTitle("Audit log")
                        .setMessage(text.length() == 0 ? "No events yet" : text.toString())
                        .setPositiveButton("Close", null).show();
            }
        });
    }

    private void run(final Call call, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JsonObject result = call.call();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(result);
                        }
                    });
                } catch (final ApiClient.ApiException error) {
                    Log.warn("AdminActivity", "Admin call failed: " + error.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toast(error.getStatus() == 403 ? "Administrator access was refused"
                                    : "Request failed: " + error.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    private interface Call {
        JsonObject call() throws ApiClient.ApiException;
    }

    private interface Callback {
        void onResult(JsonObject result);
    }
}
