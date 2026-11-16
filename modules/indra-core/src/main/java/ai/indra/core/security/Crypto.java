package ai.indra.core.security;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cryptographic primitives used by INDRA AI: PBKDF2 password hashing, HMAC
 * signatures, AES-256-GCM field encryption and constant-time comparison.
 *
 * <p>Only JDK/Android platform APIs are used (no BouncyCastle), so the same code
 * runs on the backend, on Android (API 26+) and on the Windows desktop app.</p>
 */
public final class Crypto {

    public static final Charset UTF8 = Charset.forName("UTF-8");
    public static final String HMAC_SHA256 = "HmacSHA256";

    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Crypto() {
    }

    public static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /** URL-safe random token with no padding, suitable for keys and session ids. */
    public static String randomToken(int byteCount) {
        return base64UrlEncode(randomBytes(byteCount));
    }

    public static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    public static String sha256Hex(String input) {
        return hex(sha256(input.getBytes(UTF8)));
    }

    public static String hmacSha256Hex(String key, String data) {
        return hex(hmacSha256(key.getBytes(UTF8), data.getBytes(UTF8)));
    }

    public static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(data);
        } catch (Exception error) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", error);
        }
    }

    /** PBKDF2-HMAC-SHA256 password derivation (raw bytes). */
    public static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception error) {
            throw new IllegalStateException("PBKDF2-HMAC-SHA256 unavailable", error);
        } finally {
            spec.clearPassword();
        }
    }

    /** AES-256-GCM encryption; output is {@code iv || ciphertext || tag}. */
    public static byte[] aesGcmEncrypt(byte[] key, byte[] plaintext, byte[] associatedData) {
        requireAesKey(key);
        try {
            byte[] iv = randomBytes(GCM_IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            if (associatedData != null && associatedData.length > 0) {
                cipher.updateAAD(associatedData);
            }
            byte[] cipherText = cipher.doFinal(plaintext);
            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return out;
        } catch (Exception error) {
            throw new IllegalStateException("Encryption failed", error);
        }
    }

    public static byte[] aesGcmDecrypt(byte[] key, byte[] blob, byte[] associatedData) {
        requireAesKey(key);
        if (blob == null || blob.length <= GCM_IV_BYTES) {
            throw new IllegalArgumentException("Ciphertext is too short");
        }
        try {
            byte[] iv = Arrays.copyOfRange(blob, 0, GCM_IV_BYTES);
            byte[] cipherText = Arrays.copyOfRange(blob, GCM_IV_BYTES, blob.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            if (associatedData != null && associatedData.length > 0) {
                cipher.updateAAD(associatedData);
            }
            return cipher.doFinal(cipherText);
        } catch (Exception error) {
            throw new IllegalStateException("Decryption failed (wrong key or corrupted data)", error);
        }
    }

    private static void requireAesKey(byte[] key) {
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new IllegalArgumentException("AES key must be 16, 24 or 32 bytes");
        }
    }

    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a == null ? new byte[0] : a, b == null ? new byte[0] : b);
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return constantTimeEquals(a.getBytes(UTF8), b.getBytes(UTF8));
    }

    public static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16));
            out.append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }

    public static byte[] fromHex(String hex) {
        int length = hex.length() / 2;
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) {
            out[i] = (byte) ((Character.digit(hex.charAt(i * 2), 16) << 4) | Character.digit(hex.charAt(i * 2 + 1), 16));
        }
        return out;
    }

    public static String base64UrlEncode(byte[] bytes) {
        return Base64s.encode(bytes).replace('+', '-').replace('/', '_').replace("=", "");
    }

    public static byte[] base64UrlDecode(String value) {
        String padded = value.replace('-', '+').replace('_', '/');
        int remainder = padded.length() % 4;
        if (remainder == 2) {
            padded = padded + "==";
        } else if (remainder == 3) {
            padded = padded + "=";
        }
        return Base64s.decode(padded);
    }
}
