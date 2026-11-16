package ai.indra.core.intent;

import ai.indra.core.json.JsonObject;

/** Classifier output: the intent plus any slots extracted from the sentence. */
public class IntentResult {

    private final Intent intent;
    private final double confidence;
    private final JsonObject slots;
    private final RiskLevel risk;
    private final String matchedPhrase;

    public IntentResult(Intent intent, double confidence, JsonObject slots, RiskLevel risk, String matchedPhrase) {
        this.intent = intent;
        this.confidence = confidence;
        this.slots = slots == null ? new JsonObject() : slots;
        this.risk = risk == null ? RiskLevel.SAFE : risk;
        this.matchedPhrase = matchedPhrase;
    }

    public static IntentResult chat() {
        return new IntentResult(Intent.CHAT, 0.4d, new JsonObject(), RiskLevel.SAFE, "");
    }

    public Intent getIntent() {
        return intent;
    }

    public double getConfidence() {
        return confidence;
    }

    public JsonObject getSlots() {
        return slots;
    }

    public RiskLevel getRisk() {
        return risk;
    }

    public String getMatchedPhrase() {
        return matchedPhrase;
    }

    public String slot(String key) {
        return slots.getString(key);
    }

    public long slotLong(String key, long fallback) {
        return slots.getLong(key, fallback);
    }

    /** True when the user must confirm before the app acts. */
    public boolean requiresConfirmation() {
        return risk == RiskLevel.ELEVATED || risk == RiskLevel.HIGH;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("intent", intent.name());
        json.put("confidence", Double.valueOf(confidence));
        json.put("risk", risk.name());
        json.put("matchedPhrase", matchedPhrase);
        json.put("slots", slots);
        return json;
    }
}
