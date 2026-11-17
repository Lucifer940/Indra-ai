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

    // ---- Brand palette -------------------------------------------------------
    // Premium light theme is the default: white surfaces with crimson, pink,
    // violet and electric-blue holographic accents. Dark theme keeps the same
    // accents on deep surfaces. Values are mutable so the theme can switch at
    // runtime without recreating every constant reference.
    public static int BG = Color.parseColor("#F6F7FC");
    public static int SURFACE = Color.parseColor("#FFFFFF");
    public static int SURFACE_HIGH = Color.parseColor("#FFF3F6");
    public static int ACCENT = Color.parseColor("#FF2B4E");
    public static int ACCENT_SOFT = Color.parseColor("#FFE3EA");
    public static int SECONDARY = Color.parseColor("#7C4DFF");
    public static int PINK = Color.parseColor("#FF4D8D");
    public static int BLUE = Color.parseColor("#2F80ED");
    public static int TEXT = Color.parseColor("#14142B");
    public static int TEXT_DIM = Color.parseColor("#6E7191");
    public static int STROKE = Color.parseColor("#F0DCE3");
    public static int SUCCESS = Color.parseColor("#12B76A");
    public static int WARNING = Color.parseColor("#F79009");
    public static int ERROR = Color.parseColor("#E01E37");

    private static boolean dark;

    private UiKit() {
    }

    /** True when the dark palette is active. */
    public static boolean isDark() {
        return dark;
    }

    /** Applies the light or dark palette. Called once when the app starts. */
    public static void applyTheme(boolean useDark) {
        dark = useDark;
        if (useDark) {
            BG = Color.parseColor("#0B0B16");
            SURFACE = Color.parseColor("#14141F");
            SURFACE_HIGH = Color.parseColor("#1D1D2B");
            ACCENT = Color.parseColor("#FF3B5C");
            ACCENT_SOFT = Color.parseColor("#3A1622");
            SECONDARY = Color.parseColor("#9B6BFF");
            PINK = Color.parseColor("#FF6FA5");
            BLUE = Color.parseColor("#5AA2FF");
            TEXT = Color.parseColor("#F4F4FA");
            TEXT_DIM = Color.parseColor("#9C9CB4");
            STROKE = Color.parseColor("#2A2A3C");
            SUCCESS = Color.parseColor("#3DD68C");
            WARNING = Color.parseColor("#FFB84D");
            ERROR = Color.parseColor("#FF5C7A");
        } else {
            BG = Color.parseColor("#F6F7FC");
            SURFACE = Color.parseColor("#FFFFFF");
            SURFACE_HIGH = Color.parseColor("#FFF3F6");
            ACCENT = Color.parseColor("#FF2B4E");
            ACCENT_SOFT = Color.parseColor("#FFE3EA");
            SECONDARY = Color.parseColor("#7C4DFF");
            PINK = Color.parseColor("#FF4D8D");
            BLUE = Color.parseColor("#2F80ED");
            TEXT = Color.parseColor("#14142B");
            TEXT_DIM = Color.parseColor("#6E7191");
            STROKE = Color.parseColor("#F0DCE3");
            SUCCESS = Color.parseColor("#12B76A");
            WARNING = Color.parseColor("#F79009");
            ERROR = Color.parseColor("#E01E37");
        }
    }

    /** Crimson -> pink -> violet gradient used for primary actions and the orb. */
    public static GradientDrawable gradient(int start, int end, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{start, end});
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable brandGradient(Context context, float radiusDp) {
        return corneredBrand(context, radiusDp);
    }

    private static GradientDrawable corneredBrand(Context context, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{ACCENT, PINK, SECONDARY});
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    /** Tall rounded card with a soft shadow, used for every premium surface. */
    public static LinearLayout glassCard(Context context) {
        LinearLayout card = vertical(context);
        GradientDrawable background = rounded(SURFACE, 24f, context);
        background.setStroke(Math.max(1, dp(context, 1)), STROKE);
        card.setBackground(background);
        card.setElevation(dp(context, 3));
        int pad = dp(context, 16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, 12);
        card.setLayoutParams(params);
        return card;
    }

    /** Circular icon button (bell, gear, mic) with a soft tinted background. */
    public static TextView circleButton(Context context, String glyph, int tint, int diameterDp) {
        TextView view = text(context, glyph, 18f, tint, true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(SURFACE);
        background.setStroke(Math.max(1, dp(context, 1)), STROKE);
        view.setBackground(background);
        view.setElevation(dp(context, 2));
        int size = dp(context, diameterDp);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return view;
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
        GradientDrawable background = rounded(SURFACE, 20f, context);
        background.setStroke(Math.max(1, dp(context, 1)), STROKE);
        card.setBackground(background);
        card.setElevation(dp(context, 2));
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
        TextView label = text(context, value.toUpperCase(java.util.Locale.ROOT), 11f, ACCENT, true);
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
        button.setBackground(primary ? corneredBrand(context, 26f)
                : outlined(ACCENT_SOFT, SURFACE, 26f, context));
        if (primary) {
            button.setElevation(dp(context, 3));
        }
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
        view.setBackgroundColor(STROKE);
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
