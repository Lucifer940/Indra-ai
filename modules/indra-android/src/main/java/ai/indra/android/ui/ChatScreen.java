package ai.indra.android.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ai.indra.android.MainActivity;
import ai.indra.core.model.ChatMessage;
import ai.indra.core.model.MessageRole;
import ai.indra.core.util.Times;

import java.util.List;

/** Main conversation surface with streaming bubbles and a mic button. */
public final class ChatScreen extends Screen {

    private final LinearLayout messages;
    private final ScrollView scroll;
    private final EditText input;
    private final TextView status;
    private final FrameLayout root;

    public ChatScreen(MainActivity host) {
        super(host);
        Context context = host;
        root = UiKit.root(context);

        LinearLayout column = UiKit.vertical(context);
        column.setBackgroundColor(UiKit.BG);

        LinearLayout header = UiKit.horizontal(context);
        header.setPadding(UiKit.dp(context, 16), UiKit.dp(context, 14), UiKit.dp(context, 16), UiKit.dp(context, 6));
        LinearLayout headerText = UiKit.vertical(context);
        headerText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerText.addView(UiKit.title(context, "Indra"));
        headerText.addView(UiKit.subtitle(context, "Ask anything, or hold the mic to talk"));
        header.addView(headerText);
        Button weather = UiKit.smallButton(context, "Weather");
        weather.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.showWeather();
            }
        });
        header.addView(weather);

        Button history = UiKit.smallButton(context, "History");
        history.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.showHistory();
            }
        });
        header.addView(history);
        column.addView(header);

        messages = UiKit.vertical(context);
        messages.setPadding(UiKit.dp(context, 14), UiKit.dp(context, 4), UiKit.dp(context, 14), UiKit.dp(context, 8));
        scroll = new ScrollView(context);
        scroll.addView(messages);
        column.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        status = UiKit.text(context, "", 12f, UiKit.TEXT_DIM, false);
        status.setPadding(UiKit.dp(context, 18), 0, UiKit.dp(context, 18), UiKit.dp(context, 4));
        column.addView(status);

        LinearLayout inputRow = UiKit.horizontal(context);
        inputRow.setPadding(UiKit.dp(context, 12), UiKit.dp(context, 6), UiKit.dp(context, 12), UiKit.dp(context, 14));
        input = UiKit.input(context, host.getString(ai.indra.android.R.string.chat_hint),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMaxLines(4);
        input.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        inputRow.addView(input);

        Button mic = UiKit.button(context, "🎙", false);
        mic.setContentDescription("Speak to Indra");
        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.toggleListening();
            }
        });
        inputRow.addView(mic);

        Button send = UiKit.button(context, host.getString(ai.indra.android.R.string.send), true);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = input.getText().toString().trim();
                if (!text.isEmpty()) {
                    input.setText("");
                    host.sendMessage(text, false);
                }
            }
        });
        inputRow.addView(send);
        column.addView(inputRow);

        root.addView(column);
    }

    @Override
    public View build() {
        reload();
        return root;
    }

    @Override
    public String label() {
        return "Chat";
    }

    public void reload() {
        messages.removeAllViews();
        List<ChatMessage> cached = host.store().messages(host.currentConversationId(), 60);
        if (cached.isEmpty()) {
            addBubble("Hi, I'm Indra. Ask me anything - or tap the mic and just talk.", false, true);
            addBubble("Remember: I can save things you tell me, set reminders, check weather, help with study and code, "
                    + "and on Windows I can open files and apps once you confirm.", false, true);
        } else {
            for (ChatMessage message : cached) {
                addBubble(message.getContent(), message.getRole() == MessageRole.USER, false);
            }
        }
        scrollDown();
    }

    public void addBubble(String text, boolean fromUser, boolean assistantHint) {
        Context context = host;
        LinearLayout row = UiKit.horizontal(context);
        row.setGravity(fromUser ? Gravity.END : Gravity.START);
        row.setPadding(0, UiKit.dp(context, 4), 0, UiKit.dp(context, 4));

        TextView bubble = UiKit.text(context, text, 15f, fromUser ? Color.WHITE : UiKit.TEXT, false);
        bubble.setBackground(UiKit.rounded(fromUser ? UiKit.ACCENT_SOFT : UiKit.SURFACE, 18f, context));
        bubble.setPadding(UiKit.dp(context, 14), UiKit.dp(context, 10), UiKit.dp(context, 14), UiKit.dp(context, 10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = fromUser ? 0 : UiKit.dp(context, 40);
        params.leftMargin = fromUser ? UiKit.dp(context, 40) : 0;
        bubble.setLayoutParams(params);
        bubble.setTextIsSelectable(true);
        if (!fromUser) {
            bubble.setContentDescription("Indra said: " + text);
        }
        row.addView(bubble);
        messages.addView(row);
    }

    public void updateLastAssistantBubble(String text) {
        if (messages.getChildCount() == 0) {
            addBubble(text, false, false);
            return;
        }
        View last = messages.getChildAt(messages.getChildCount() - 1);
        if (last instanceof LinearLayout) {
            LinearLayout row = (LinearLayout) last;
            if (row.getChildCount() > 0 && row.getChildAt(0) instanceof TextView) {
                ((TextView) row.getChildAt(0)).setText(text);
                scrollDown();
                return;
            }
        }
        addBubble(text, false, false);
    }

    public void setStatus(String text) {
        status.setText(text == null ? "" : text);
    }

    public void scrollDown() {
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    public void setInput(String text) {
        input.setText(text);
        input.setSelection(input.getText().length());
    }

    /** Timestamp helper used by exported transcripts. */
    public String stamp() {
        return Times.toDisplay(System.currentTimeMillis(), java.util.TimeZone.getDefault().getID());
    }
}
