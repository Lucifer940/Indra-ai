package ai.indra.android.ui;

import android.graphics.Color;

/**
 * The six holographic looks of the INDRA orb ("AI Aura").
 *
 * <p>Each style carries its own gradient ring and glow colours; the animation speed
 * is a user setting. Persisted by id, so new styles can be added without breaking
 * saved preferences.</p>
 */
public enum AuraStyle {

    ENERGY("Energy", "#FF2B4E", "#FF7A9A", "#7C4DFF"),
    GALAXY("Galaxy", "#5B4BFF", "#B45BFF", "#2F80ED"),
    FIRE("Fire", "#FF6A00", "#FF2B4E", "#FFD166"),
    CRYSTAL("Crystal", "#34D8FF", "#8B7BFF", "#E6F7FF"),
    NEON("Neon", "#00E5C0", "#7C4DFF", "#FF2B4E"),
    AURORA("Aurora", "#12B76A", "#34D8FF", "#B45BFF");

    private final String label;
    private final int inner;
    private final int middle;
    private final int outer;

    AuraStyle(String label, String inner, String middle, String outer) {
        this.label = label;
        this.inner = Color.parseColor(inner);
        this.middle = Color.parseColor(middle);
        this.outer = Color.parseColor(outer);
    }

    public String getLabel() {
        return label;
    }

    public int getInner() {
        return inner;
    }

    public int getMiddle() {
        return middle;
    }

    public int getOuter() {
        return outer;
    }

    public int[] colors() {
        return new int[]{inner, middle, outer};
    }

    public static AuraStyle fromId(String id) {
        if (id != null) {
            for (AuraStyle style : values()) {
                if (style.name().equalsIgnoreCase(id.trim())) {
                    return style;
                }
            }
        }
        return ENERGY;
    }

    public static String[] labels() {
        AuraStyle[] all = values();
        String[] names = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            names[i] = all[i].label;
        }
        return names;
    }

    public static AuraStyle fromLabel(String label) {
        if (label != null) {
            for (AuraStyle style : values()) {
                if (style.label.equalsIgnoreCase(label.trim())) {
                    return style;
                }
            }
        }
        return ENERGY;
    }
}
