package ai.indra.core.security;

import ai.indra.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoSecurityTest {

    @Test
    void base64RoundTripsAllByteValues() {
        byte[] data = new byte[512];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        String encoded = Base64s.encode(data);
        byte[] decoded = Base64s.decode(encoded);
        assertTrue(java.util.Arrays.equals(data, decoded));
    }

    @Test
    void base64UrlMatchesJdk() {
        byte[] data = "indra-ai-secret".getBytes(StandardCharsets.UTF_8);
        String ours = Crypto.base64UrlEncode(data);
        String jdk = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data);
        assertEquals(jdk, ours);
    }

    @Test
    void aesGcmEncryptsAndDetectsTampering() {
        byte[] key = Crypto.randomBytes(32);
        byte[] aad = "user-1".getBytes(StandardCharsets.UTF_8);
        byte[] blob = Crypto.aesGcmEncrypt(key, "top secret memory".getBytes(StandardCharsets.UTF_8), aad);
        String plain = new String(Crypto.aesGcmDecrypt(key, blob, aad), StandardCharsets.UTF_8);
        assertEquals("top secret memory", plain);

        blob[blob.length - 1] = (byte) (blob[blob.length - 1] ^ 0xFF);
        assertThrows(IllegalStateException.class, () -> Crypto.aesGcmDecrypt(key, blob, aad));
    }

    @Test
    void aesRejectsWrongKeyLength() {
        assertThrows(IllegalArgumentException.class,
                () -> Crypto.aesGcmEncrypt(new byte[7], new byte[]{1}, null));
    }

    @Test
    void hmacIsDeterministicAndKeyDependent() {
        String a = Crypto.hmacSha256Hex("key-one", "payload");
        String b = Crypto.hmacSha256Hex("key-one", "payload");
        String c = Crypto.hmacSha256Hex("key-two", "payload");
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void passwordHashesVerifyAndUseUniqueSalts() {
        PasswordHasher hasher = new PasswordHasher(10000);
        String first = hasher.hash("CorrectHorseBattery9!");
        String second = hasher.hash("CorrectHorseBattery9!");
        assertNotEquals(first, second);
        assertTrue(hasher.verify(first, "CorrectHorseBattery9!"));
        assertFalse(hasher.verify(first, "wrong-password"));
        assertFalse(hasher.verify("garbage", "CorrectHorseBattery9!"));
        assertTrue(first.startsWith("pbkdf2_sha256$10000$"));
    }

    @Test
    void passwordHasherDetectsWeakStoredHash() {
        PasswordHasher weak = new PasswordHasher(10000);
        PasswordHasher strong = new PasswordHasher(20000);
        assertTrue(strong.needsRehash(weak.hash("password1234")));
        assertFalse(strong.needsRehash(strong.hash("password1234")));
    }

    @Test
    void jwtSignsVerifiesAndRejectsTampering() {
        Jwt jwt = new Jwt("0123456789abcdef0123456789abcdef", "indra-test", 60L);
        JsonObject claims = new JsonObject();
        claims.put("sub", "user-42");
        claims.put("role", "ADMIN");
        String token = jwt.sign(claims);

        JsonObject verified = jwt.verify(token);
        assertEquals("user-42", verified.getString("sub"));
        assertEquals("ADMIN", verified.getString("role"));
        assertEquals("indra-test", verified.getString("iss"));

        assertThrows(Jwt.TokenException.class, () -> jwt.verify(token + "x"));
        assertThrows(Jwt.TokenException.class, () -> jwt.verify("not.a.token"));
    }

    @Test
    void expiredJwtIsRejected() {
        Jwt jwt = new Jwt("0123456789abcdef0123456789abcdef", "indra-test", 1L);
        JsonObject payload = new JsonObject();
        payload.put("sub", "user-1");
        payload.put("exp", Long.valueOf((System.currentTimeMillis() / 1000L) - 10L));
        String token = jwt.sign(payload);
        assertThrows(Jwt.TokenException.class, () -> jwt.verify(token));
    }

    @Test
    void jwtRequiresLongSecret() {
        assertThrows(IllegalArgumentException.class, () -> new Jwt("short", "iss", 60L));
    }

    @Test
    void validationEnforcesEmailAndPasswordPolicy() {
        assertEquals("user@example.com", Validation.requireEmail("email", "  User@Example.com "));
        assertThrows(ValidationException.class, () -> Validation.requireEmail("email", "not-an-email"));
        assertThrows(ValidationException.class, () -> Validation.requirePassword("password", "short"));
        assertThrows(ValidationException.class, () -> Validation.requirePassword("password", "nodigitshere"));
        assertThrows(ValidationException.class, () -> Validation.requirePassword("password", "password1234"));
        assertEquals("GoodPass123", Validation.requirePassword("password", "GoodPass123"));
    }

    @Test
    void sanitizeStripsControlCharacters() {
        assertEquals("hello world", Validation.sanitize("  hello\u0007 world  "));
        assertEquals(null, Validation.sanitize("   "));
    }

    @Test
    void idsAreUniqueAndRandom() {
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (int i = 0; i < 200; i++) {
            assertTrue(seen.add(ai.indra.core.util.Ids.uuid()));
            assertTrue(seen.add(ai.indra.core.util.Ids.random(16)));
        }
    }
}
