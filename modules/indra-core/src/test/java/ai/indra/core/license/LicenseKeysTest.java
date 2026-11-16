package ai.indra.core.license;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseKeysTest {

    @Test
    void generatedKeysHaveValidFormatAndAreUnique() {
        Set<String> keys = new HashSet<String>();
        for (int i = 0; i < 200; i++) {
            String key = LicenseKeys.generate();
            assertTrue(key.startsWith("INDRA-"), key);
            assertTrue(LicenseKeys.isValidFormat(key), key);
            assertEquals(5, key.split("-").length);
            keys.add(key);
        }
        assertEquals(200, keys.size());
    }

    @Test
    void typoIsDetectedByChecksum() {
        String key = LicenseKeys.generate();
        String[] parts = key.split("-");
        char original = parts[1].charAt(0);
        char replacement = original == 'A' ? 'B' : 'A';
        String tampered = "INDRA-" + replacement + parts[1].substring(1) + "-" + parts[2] + "-" + parts[3] + "-" + parts[4];
        assertFalse(LicenseKeys.isValidFormat(tampered));
    }

    @Test
    void normalisationAcceptsLooseInput() {
        String key = LicenseKeys.generate();
        String loose = key.toLowerCase().replace("-", " ");
        assertEquals(key, LicenseKeys.normalize(loose));
        assertTrue(LicenseKeys.isValidFormat(loose));
    }

    @Test
    void invalidInputsRejected() {
        assertFalse(LicenseKeys.isValidFormat(null));
        assertFalse(LicenseKeys.isValidFormat(""));
        assertFalse(LicenseKeys.isValidFormat("INDRA-1234-5678-9012-3456"));
        assertFalse(LicenseKeys.isValidFormat("WRONG-ABCD-EFGH-IJKL-MNOP"));
    }

    @Test
    void maskingNeverRevealsFullKey() {
        String key = LicenseKeys.generate();
        String masked = LicenseKeys.mask(key);
        assertTrue(masked.startsWith("INDRA-****"));
        assertFalse(masked.equals(key));
    }

    @Test
    void fingerprintIsStableButNotTheKey() {
        String key = LicenseKeys.generate();
        assertEquals(LicenseKeys.fingerprint(key), LicenseKeys.fingerprint(key.toLowerCase()));
        assertFalse(LicenseKeys.fingerprint(key).contains("INDRA"));
    }
}
