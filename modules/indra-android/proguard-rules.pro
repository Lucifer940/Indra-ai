# INDRA AI release rules: the codebase uses no reflection-based serialisation,
# so only Android/JavaFX entry points need keeping.
-keep class ai.indra.android.** { *; }
-keep class ai.indra.core.** { *; }
-dontwarn org.jetbrains.annotations.**
