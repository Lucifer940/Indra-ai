package ai.indra.android.ui;

import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ai.indra.android.MainActivity;
import ai.indra.android.R;

/**
 * Home screen: the premium landing surface.
 *
 * <p>Header logo, notification and settings shortcuts, the animated aura orb, a
 * personalised greeting, the chat composer, and real quick-action cards (voice,
 * memory, tasks, aura). Every control routes to an existing feature - nothing here
 * is decorative-only.</p>
 */
public final class HomeScreen extends Screen {

    private final AuraOrbView orb;

    public HomeScreen(MainActivity host) {
        super(host);
        this.orb = new AuraOrbView(host);
        this.orb.setAura(AuraStyle.fromId(host.prefs().getAuraStyle()));
        this.orb.setSpeed(host.prefs().getAuraSpeed());
    }

    @Override
    public String label() {
        return "Home";
    }

    @Override
    public View build() {
        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(UiKit.BG);

        LinearLayout column = UiKit.vertical(host);
        column.setPadding(UiKit.dp(host, 18), UiKit.dp(host, 10), UiKit.dp(host, 18), UiKit.dp(host, 120));

        column.addView(header());
        column.addView(orbBlock());
        column.addView(greeting());
        column.addView(suggestions());
        column.addView(composer());
        column.addView(moreSection());
        column.addView(quickTools());

        scroll.addView(column);
        return scroll;
    }

    /** Logo on the left, notification + settings shortcuts on the right. */
    private View header() {
        LinearLayout row = UiKit.horizontal(host);
        row.setPadding(0, 0, 0, UiKit.dp(host, 6));

        FrameLayout avatar = new FrameLayout(host);
        avatar.setBackground(UiKit.brandGradient(host, 30f));
        avatar.setPadding(UiKit.dp(host, 2), UiKit.dp(host, 2), UiKit.dp(host, 2), UiKit.dp(host, 2));
        ImageView logo = new ImageView(host);
        logo.setImageResource(R.drawable.indra_orb_face);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.addView(logo, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(UiKit.dp(host, 50), UiKit.dp(host, 50));
        avatar.setLayoutParams(logoParams);
        row.addView(avatar);

        LinearLayout brand = UiKit.vertical(host);
        brand.setPadding(UiKit.dp(host, 10), 0, 0, 0);
        brand.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        brand.addView(UiKit.text(host, "INDRA AI", 20f, UiKit.TEXT, true));
        TextView tagline = UiKit.text(host, host.getString(R.string.tagline), 9f, UiKit.TEXT_DIM, false);
        tagline.setLetterSpacing(0.16f);
        brand.addView(tagline);
        row.addView(brand);

        TextView bell = UiKit.circleButton(host, "🔔", UiKit.ACCENT, 42);
        bell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.showNotifications();
            }
        });
        row.addView(bell);
        row.addView(spacer(8));

        TextView gear = UiKit.circleButton(host, "⚙", UiKit.SECONDARY, 42);
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openTab(4);
            }
        });
        row.addView(gear);
        return row;
    }

    private View spacer(int dp) {
        View view = new View(host);
        view.setLayoutParams(new LinearLayout.LayoutParams(UiKit.dp(host, dp), 1));
        return view;
    }

    /**
     * Three live starters: tapping one sends that message and opens the conversation, so the
     * empty space on tall screens does real work instead of padding.
     */
    private View suggestions() {
        LinearLayout row = UiKit.horizontal(host);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, 0, 0, UiKit.dp(host, 16));

        final String[] starters = {
                "Summarise my day",
                "Help me plan something",
                "What do you remember?",
        };
        for (int i = 0; i < starters.length; i++) {
            final String text = starters[i];
            TextView chip = UiKit.chip(host, text, UiKit.SECONDARY);
            chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
            chip.setPadding(UiKit.dp(host, 14), UiKit.dp(host, 9), UiKit.dp(host, 14), UiKit.dp(host, 9));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                params.leftMargin = UiKit.dp(host, 8);
            }
            chip.setLayoutParams(params);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.sendMessage(text, false);
                    host.openTab(1);
                }
            });
            row.addView(chip);
        }
        return row;
    }

    private View orbBlock() {
        // The orb owns the flexible space: tall phones get a larger hologram, small phones
        // keep the 280dp minimum and simply scroll.
        FrameLayout frame = new FrameLayout(host);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        params.topMargin = UiKit.dp(host, 4);
        params.bottomMargin = UiKit.dp(host, 8);
        frame.setLayoutParams(params);
        frame.setMinimumHeight(UiKit.dp(host, 280));

        orb.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        frame.addView(orb);

        TextView chip = UiKit.chip(host, "AI Aura \u00b7 " + orb.getAura().getLabel(), UiKit.SECONDARY);
        FrameLayout.LayoutParams chipParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        chipParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        chipParams.bottomMargin = UiKit.dp(host, 2);
        chip.setLayoutParams(chipParams);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.showAuraPicker();
            }
        });
        frame.addView(chip);
        return frame;
    }

    private View greeting() {
        LinearLayout column = UiKit.vertical(host);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(0, UiKit.dp(host, 10), 0, UiKit.dp(host, 14));

        String name = host.prefs().getDisplayName();
        String hello = name == null || name.trim().isEmpty() ? "Hello!" : "Hello, " + firstWord(name) + "!";
        TextView title = UiKit.text(host, hello, 30f, UiKit.TEXT, true);
        title.setGravity(Gravity.CENTER);
        column.addView(title);

        TextView question = UiKit.text(host, "How can I help you today?", 16f, UiKit.TEXT_DIM, false);
        question.setGravity(Gravity.CENTER);
        column.addView(question);
        return column;
    }

    /** Composer: attach shortcut, text field, gradient send button. */
    private View composer() {
        LinearLayout row = UiKit.horizontal(host);
        applySurface(row);

        TextView attach = UiKit.text(host, "📎", 18f, UiKit.TEXT_DIM, false);
        attach.setPadding(UiKit.dp(host, 6), 0, UiKit.dp(host, 8), 0);
        attach.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openTab(1);
                host.toast("Attach a photo or file from the Chat tab");
            }
        });
        row.addView(attach);

        final EditText input = new EditText(host);
        input.setHint(host.getString(R.string.chat_hint));
        input.setHintTextColor(UiKit.TEXT_DIM);
        input.setTextColor(UiKit.TEXT);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        input.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(input);

        final TextView send = UiKit.text(host, "➤", 18f, Color.WHITE, true);
        send.setGravity(Gravity.CENTER);
        send.setBackground(UiKit.brandGradient(host, 26f));
        send.setElevation(UiKit.dp(host, 3));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                UiKit.dp(host, 44), UiKit.dp(host, 44));
        sendParams.leftMargin = UiKit.dp(host, 8);
        send.setLayoutParams(sendParams);
        final Runnable submit = new Runnable() {
            @Override
            public void run() {
                String text = input.getText().toString().trim();
                if (text.isEmpty()) {
                    return;
                }
                input.setText("");
                host.sendMessage(text, false);
                host.openTab(1);
            }
        };
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit.run();
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                submit.run();
                return true;
            }
        });
        row.addView(send);

        LinearLayout wrap = UiKit.vertical(host);
        wrap.setPadding(0, 0, 0, UiKit.dp(host, 16));
        wrap.addView(row);
        return wrap;
    }

    /** "More" cards - every card opens a feature that actually exists. */
    private View moreSection() {
        LinearLayout column = UiKit.vertical(host);
        column.addView(UiKit.text(host, "More", 20f, UiKit.TEXT, true));

        LinearLayout row = UiKit.horizontal(host);
        row.setPadding(0, UiKit.dp(host, 10), 0, 0);
        row.addView(bigCard("🎙", "Voice Mode", "Talk hands-free",
                UiKit.withAlpha(UiKit.ACCENT, 30), UiKit.ACCENT, new Runnable() {
                    @Override
                    public void run() {
                        host.openTab(2);
                    }
                }));
        row.addView(bigCard("🧠", "Memory", "What INDRA remembers",
                UiKit.withAlpha(UiKit.SECONDARY, 30), UiKit.SECONDARY, new Runnable() {
                    @Override
                    public void run() {
                        host.openTab(5);
                    }
                }));
        column.addView(row);

        LinearLayout row2 = UiKit.horizontal(host);
        row2.setPadding(0, UiKit.dp(host, 4), 0, 0);
        row2.addView(bigCard("⏰", "Triggers", "Reminders & tasks",
                UiKit.withAlpha(UiKit.BLUE, 30), UiKit.BLUE, new Runnable() {
                    @Override
                    public void run() {
                        host.openTab(3);
                    }
                }));
        row2.addView(bigCard("✨", "AI Aura", "Orb style & colours",
                UiKit.withAlpha(UiKit.PINK, 30), UiKit.PINK, new Runnable() {
                    @Override
                    public void run() {
                        host.showAuraPicker();
                    }
                }));
        column.addView(row2);
        return column;
    }

    private View quickTools() {
        LinearLayout card = UiKit.glassCard(host);
        card.addView(UiKit.text(host, "Quick tools", 15f, UiKit.TEXT, true));
        card.addView(row("🖼  Whiteboard", new Runnable() {
            @Override
            public void run() {
                host.showWhiteboard();
            }
        }));
        card.addView(row("🌤  Weather", new Runnable() {
            @Override
            public void run() {
                host.showWeather();
            }
        }));
        card.addView(row("💬  Conversation history", new Runnable() {
            @Override
            public void run() {
                host.showHistory();
            }
        }));
        card.addView(row("🔑  API keys & cloud", new Runnable() {
            @Override
            public void run() {
                host.openTab(4);
            }
        }));
        return card;
    }

    private View row(String label, final Runnable action) {
        TextView item = UiKit.text(host, label, 14f, UiKit.TEXT, false);
        item.setPadding(0, UiKit.dp(host, 10), 0, UiKit.dp(host, 10));
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return item;
    }

    private View bigCard(String glyph, String title, String subtitle, int iconBg, int tint,
                         final Runnable action) {
        LinearLayout card = UiKit.vertical(host);
        applySurface(card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.rightMargin = UiKit.dp(host, 6);
        params.leftMargin = UiKit.dp(host, 6);
        card.setLayoutParams(params);

        TextView icon = UiKit.text(host, glyph, 20f, tint, false);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(UiKit.rounded(iconBg, 16f, host));
        icon.setLayoutParams(new LinearLayout.LayoutParams(UiKit.dp(host, 44), UiKit.dp(host, 44)));
        card.addView(icon);
        TextView name = UiKit.text(host, title, 16f, UiKit.TEXT, true);
        name.setPadding(0, UiKit.dp(host, 10), 0, 0);
        card.addView(name);
        card.addView(UiKit.text(host, subtitle, 12f, UiKit.TEXT_DIM, false));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return card;
    }

    private static String firstWord(String value) {
        String trimmed = value.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    /** Rounded, hairline-bordered surface shared by the composer and the cards. */
    private void applySurface(View view) {
        android.graphics.drawable.GradientDrawable background =
                UiKit.rounded(UiKit.SURFACE, 22f, host);
        background.setStroke(Math.max(1, UiKit.dp(host, 1)), UiKit.STROKE);
        view.setBackground(background);
        view.setElevation(UiKit.dp(host, 2));
        if (view instanceof LinearLayout) {
            int pad = UiKit.dp(host, 14);
            ((LinearLayout) view).setPadding(pad, pad, pad, pad);
        }
    }
}
