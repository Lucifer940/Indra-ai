package ai.indra.android.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import ai.indra.android.MainActivity;
import ai.indra.core.model.MemoryItem;
import ai.indra.core.util.Times;

import java.util.List;

/** Long-term memory management: see, add, edit and delete what Indra remembers. */
public final class MemoryScreen extends Screen {

    private final ScrollView root;
    private final LinearLayout list;

    public MemoryScreen(MainActivity host) {
        super(host);
        Context context = host;
        LinearLayout column = UiKit.screen(context, 16);
        column.setBackgroundColor(UiKit.BG);

        LinearLayout header = UiKit.horizontal(context);
        header.addView(UiKit.title(context, "Memory"));
        column.addView(header);
        column.addView(UiKit.subtitle(context, "Approved facts Indra uses in conversations. Stored encrypted on this phone and synced to your account."));

        column.addView(UiKit.toggleRow(context, "Memory enabled",
                "Turn off to stop using or saving memories",
                host.prefs().isMemoryEnabled(), new UiKit.ToggleListener() {
                    @Override
                    public void onChanged(boolean checked) {
                        host.prefs().setLocalSettings(host.prefs().getPersona(), host.prefs().getBrain(),
                                host.prefs().getLanguage(), host.prefs().getTheme(), host.prefs().getProvider(),
                                host.prefs().getModel(), checked, host.prefs().isAutoMemory(),
                                host.prefs().isAutoSpeak(), host.prefs().getSpeechRate(), host.prefs().getSpeechPitch());
                        host.session().pushSettings();
                    }
                }));

        column.addView(UiKit.toggleRow(context, "Learn automatically",
                "Save useful personal facts without asking",
                host.prefs().isAutoMemory(), new UiKit.ToggleListener() {
                    @Override
                    public void onChanged(boolean checked) {
                        host.prefs().setLocalSettings(host.prefs().getPersona(), host.prefs().getBrain(),
                                host.prefs().getLanguage(), host.prefs().getTheme(), host.prefs().getProvider(),
                                host.prefs().getModel(), host.prefs().isMemoryEnabled(), checked,
                                host.prefs().isAutoSpeak(), host.prefs().getSpeechRate(), host.prefs().getSpeechPitch());
                        host.session().pushSettings();
                    }
                }));

        LinearLayout actions = UiKit.horizontal(context);
        Button add = UiKit.button(context, "Add memory", true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddDialog();
            }
        });
        actions.addView(add);
        Button clear = UiKit.button(context, "Delete all", false);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });
        actions.addView(clear);
        column.addView(actions);

        list = UiKit.vertical(context);
        column.addView(list);
        root = UiKit.scroll(context, column);
    }

    @Override
    public View build() {
        reload();
        return root;
    }

    @Override
    public String label() {
        return "Memory";
    }

    public void reload() {
        list.removeAllViews();
        List<MemoryItem> memories = host.store().memories();
        if (memories.isEmpty()) {
            list.addView(emptyCard());
            return;
        }
        Context context = host;
        for (final MemoryItem item : memories) {
            LinearLayout card = UiKit.card(context);
            LinearLayout top = UiKit.horizontal(context);
            LinearLayout texts = UiKit.vertical(context);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            texts.addView(UiKit.text(context, item.getContent(), 15f, UiKit.TEXT, false));
            texts.addView(UiKit.text(context, item.getCategory().name().toLowerCase(java.util.Locale.ROOT)
                    + " · importance " + item.getImportance() + " · " + Times.toDisplay(item.getUpdatedAt(),
                    java.util.TimeZone.getDefault().getID()), 11f, UiKit.TEXT_DIM, false));
            top.addView(texts);
            Button edit = UiKit.smallButton(context, "Edit");
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEditDialog(item);
                }
            });
            top.addView(edit);
            Button delete = UiKit.smallButton(context, "Delete");
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete(item);
                }
            });
            top.addView(delete);
            card.addView(top);
            list.addView(card);
        }
    }

    private View emptyCard() {
        LinearLayout card = UiKit.card(host);
        card.addView(UiKit.text(host, "Nothing saved yet", 15f, UiKit.TEXT, true));
        card.addView(UiKit.subtitle(host, "Say \"Remember that I prefer tea over coffee\" and it will appear here."));
        return card;
    }

    private void showAddDialog() {
        Context context = host;
        LinearLayout form = UiKit.vertical(context);
        form.setPadding(UiKit.dp(context, 16), UiKit.dp(context, 8), UiKit.dp(context, 16), 0);
        final EditText content = UiKit.input(context, "What should I remember?", android.text.InputType.TYPE_CLASS_TEXT);
        form.addView(content);
        final EditText category = UiKit.singleLine(context, "Category (PROFILE, PREFERENCE, GOAL, OTHER…)");
        form.addView(category);
        new AlertDialog.Builder(context)
                .setTitle("Add memory")
                .setView(form)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String text = content.getText().toString().trim();
                        if (text.isEmpty()) {
                            return;
                        }
                        String cat = category.getText().toString().trim();
                        host.createMemory(text, cat.isEmpty()
                                ? ai.indra.core.memory.MemoryEngine.classify(text).name() : cat.toUpperCase(java.util.Locale.ROOT));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditDialog(final MemoryItem item) {
        Context context = host;
        final EditText input = UiKit.input(context, "Memory", android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(item.getContent());
        new AlertDialog.Builder(context)
                .setTitle("Edit memory")
                .setView(input)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        host.updateMemory(item.getId(), input.getText().toString().trim(), item.getCategory().name());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(final MemoryItem item) {
        new AlertDialog.Builder(host)
                .setTitle("Delete memory?")
                .setMessage(item.getContent())
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        host.deleteMemory(item.getId());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(host)
                .setTitle("Delete all memories?")
                .setMessage("This removes every saved memory from this device and your account. It cannot be undone.")
                .setPositiveButton("Delete all", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        host.clearMemories();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
