package ai.indra.core.config;

import ai.indra.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteConfigTest {

    @Test
    void comparesVersionsNumerically() {
        assertTrue(RemoteConfig.compareVersions("1.10.0", "1.9.9") > 0);
        assertTrue(RemoteConfig.compareVersions("1.0.0", "1.0.1") < 0);
        assertEquals(0, RemoteConfig.compareVersions("1.1.0", "1.1"));
        assertTrue(RemoteConfig.compareVersions("2.0.0-beta", "1.9.9") > 0);
    }

    @Test
    void forcesUpdateWhenBelowMinimum() {
        RemoteConfig config = RemoteConfig.defaults().setMinVersion("1.2.0").setLatestVersion("1.3.0");
        assertTrue(config.requiresUpdate("1.1.9"));
        assertFalse(config.requiresUpdate("1.2.0"));
        assertTrue(config.hasNewerVersion("1.2.5"));
        assertFalse(config.hasNewerVersion("1.3.0"));
    }

    @Test
    void featureFlagsAndRemoteTogglesSurviveRoundTrip() {
        RemoteConfig config = RemoteConfig.defaults()
                .setAnnouncement("New brain modes released")
                .setMaintenanceMessage("")
                .setGoogleLoginEnabled(true)
                .setGithubLoginEnabled(true)
                .setMemoryEnabled(false);
        RemoteConfig parsed = RemoteConfig.fromJson(JsonObject.parse(config.toJson()));
        assertTrue(parsed.isGoogleLoginEnabled());
        assertTrue(parsed.isGithubLoginEnabled());
        assertFalse(parsed.isMemoryEnabled());
        assertEquals("New brain modes released", parsed.getAnnouncement());
        assertTrue(parsed.isFeatureEnabled("whiteboard"));
        assertFalse(parsed.isFeatureEnabled("unknownFeature"));
        assertFalse(parsed.isMaintenance());
    }
}
