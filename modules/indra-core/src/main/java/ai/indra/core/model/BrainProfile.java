package ai.indra.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * "Brain" profiles - reasoning styles layered on top of the persona. They are
 * inspired by famous fictional AIs and are implemented purely as prompt and
 * delivery shaping (no imitation of any proprietary system).
 */
public enum BrainProfile {
    CORE("core", "Core Brain", "Balanced all-round reasoning (default)"),
    STRATEGIC("strategic", "Jarvis Brain", "Strategic planning, options, calm competence"),
    TACTICAL("tactical", "Edith Brain", "Fast tactical analysis with risk flags"),
    SOCIAL("social", "Friday Brain", "Warm, quick, people-focused problem solving"),
    ANALYTIC("analytic", "Vision Brain", "Deep analytical breakdowns and structured reasoning"),
    ULTRON("ultron", "Ultron Brain", "Relentless efficiency: blunt, decisive, no filler");

    private final String id;
    private final String label;
    private final String description;

    BrainProfile(String id, String label, String description) {
        this.id = id;
        this.label = label;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public static BrainProfile fromId(String id) {
        if (id != null) {
            String normalized = id.trim().toLowerCase(java.util.Locale.ROOT);
            for (BrainProfile profile : values()) {
                if (profile.id.equals(normalized) || profile.name().equalsIgnoreCase(normalized)) {
                    return profile;
                }
            }
        }
        return CORE;
    }

    public static List<String> ids() {
        List<String> ids = new ArrayList<String>();
        for (BrainProfile profile : values()) {
            ids.add(profile.id);
        }
        return ids;
    }
}
