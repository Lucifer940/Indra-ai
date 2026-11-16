package ai.indra.android.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Small view factory used by every screen. Building the UI in Java keeps the
 * layout logic in one place, adapts to any screen size and avoids a forest of
 * XML files that would drift apart from the behaviour.
 */
public final class UiKit {

    public static final int BG = Color.parseColor("#080B18");
    public static final int SURFACE = Color.parseColor("#111629");
    public static final int SURFACE_HIGH = Color.parseColor("#1A2138");
    public static final int ACCENT = Color.parseColor("#5B8CFF");
    public static final int ACCENT_SOFT = Color.parseColor("#22305C");
    public static final int SECONDARY = Color.parseColor("#8A5BFF");
    public static final int TEXT = Color.parseColor("#E9EDFF");
    public static final int TEXT_DIM = Color.parseColor("#9AA3C7");
    public static final int SUCCESS = Color.parseColor("#3DD68C");
    public static final int WARNING = Color.parseColor("#FFB84D");
    public static final int ERROR = Color.parseColor("#FF5C7A");

    private UiKit() {
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable outlined(int strokeColor, int fillColor, float radiusDp, Context context) {
        GradientDrawable drawable = rounded(fillColor, radiusDp, context);
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }

    public static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static LinearLayout screen(Context context, int paddingDp) {
        LinearLayout layout = vertical(context);
        layout.setPadding(dp(context, paddingDp), dp(context, paddingDp), dp(context, paddingDp), dp(context, paddingDp));
        return layout;
    }

    public static LinearLayout card(Context context) {
        LinearLayout card = vertical(context);
        card.setBackground(rounded(SURFACE, 18f, context));
        int pad = dp(context, 14);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, 10);
        card.setLayoutParams(params);
        return card;
    }

    public static TextView text(Context context, String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(dp(context, 2), 1f);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    public static TextView title(Context context, String value) {
        return text(context, value, 22f, TEXT, true);
    }

    public static TextView subtitle(Context context, String value) {
        return text(context, value, 13f, TEXT_DIM, false);
    }

    public static TextView sectionLabel(Context context, String value) {
        TextView label = text(context, value.toUpperCase(java.util.Locale.ROOT), 11f, TEXT_DIM, true);
        label.setLetterSpacing(0.12f);
        label.setPadding(0, dp(context, 14), 0, dp(context, 6));
        return label;
    }

    public static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        button.setTextColor(primary ? Color.WHITE : ACCENT);
        button.setBackground(primary ? rounded(ACCENT, 24f, context) : outlined(ACCENT_SOFT, SURFACE, 24f, context));
        button.setMinHeight(dp(context, 46));
        button.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(context, 8);
        params.bottomMargin = dp(context, 6);
        button.setLayoutParams(params);
        return button;
    }

    public static Button smallButton(Context context, String label) {
        Button button = button(context, label, false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        button.setMinHeight(dp(context, 34));
        return button;
    }

    public static EditText input(Context context, String hint, int inputType) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setHintTextColor(TEXT_DIM);
        input.setTextColor(TEXT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        input.setInputType(inputType);
        input.setBackground(rounded(SURFACE_HIGH, 14f, context));
        int pad = dp(context, 12);
        input.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, 8);
        input.setLayoutParams(params);
        return input;
    }

    public static EditText singleLine(Context context, String hint) {
        return input(context, hint, InputType.TYPE_CLASS_TEXT);
    }

    public static EditText password(Context context, String hint) {
        EditText input = input(context, hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSingleLine(true);
        return input;
    }

    public static Switch toggle(Context context, boolean checked) {
        Switch toggle = new Switch(context);
        toggle.setChecked(checked);
        return toggle;
    }

    /** Row with a label, optional description and a switch. */
    public static LinearLayout toggleRow(final Context context, String label, String description,
                                         boolean checked, final ToggleListener listener) {
        LinearLayout row = horizontal(context);
        row.setPadding(0, dp(context, 8), 0, dp(context, 8));
        LinearLayout texts = vertical(context);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        texts.addView(text(context, label, 15f, TEXT, false));
        if (description != null && !description.isEmpty()) {
            texts.addView(text(context, description, 12f, TEXT_DIM, false));
        }
        row.addView(texts);
        final Switch toggle = toggle(context, checked);
        toggle.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                listener.onChanged(isChecked);
            }
        });
        row.addView(toggle);
        return row;
    }

    /** Callback for toggle rows. */
    public interface ToggleListener {
        void onChanged(boolean checked);
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(SURFACE_HIGH);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 0.6f)));
        params.topMargin = dp(context, 10);
        params.bottomMargin = dp(context, 10);
        view.setLayoutParams(params);
        return view;
    }

    public static View space(Context context, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, heightDp)));
        return view;
    }

    public static ScrollView scroll(Context context, View content) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(content);
        scroll.setBackgroundColor(BG);
        return scroll;
    }

    public static HorizontalScrollView horizontalScroll(Context context) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setFillViewport(false);
        return scroll;
    }

    public static FrameLayout root(Context context) {
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(BG);
        return frame;
    }

    /** Small rounded status pill, e.g. "Listening…". */
    public static TextView chip(Context context, String label, int color) {
        TextView chip = text(context, label, 12f, color, true);
        chip.setBackground(rounded(withAlpha(color, 40), 12f, context));
        chip.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        return chip;
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static TextView bodyText(Context context, String value) {
        TextView view = text(context, value, 15f, TEXT, false);
        view.setPadding(0, dp(context, 4), 0, dp(context, 4));
        return view;
    }
}
