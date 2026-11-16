package ai.indra.core.intent;

/** How much care an action needs before it is executed. */
public enum RiskLevel {
    /** Pure information - no confirmation needed. */
    SAFE,
    /** Reads or opens things - a single confirmation at most. */
    LOW,
    /** Changes something on the device - always confirm. */
    ELEVATED,
    /** Destructive, costly or privacy sensitive - explicit confirmation required. */
    HIGH
}
