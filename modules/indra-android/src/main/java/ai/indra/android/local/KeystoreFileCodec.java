package ai.indra.android.local;

import android.util.Base64;

import ai.indra.backend.store.FileCodec;
import ai.indra.core.apikeys.SecretCipher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * Encrypts the embedded engine's document store with the Android Keystore.
 *
 * <p>Every collection file (accounts, memories, tasks, settings, devices) is written
 * as {@code INDRA1:<keystore ciphertext>}. The AES-256-GCM key lives in the
 * hardware-backed Keystore, so a copy of the app's files directory is useless
 * without the device. Files written before encryption existed are still readable:
 * anything without the prefix is treated as plain JSON.</p>
 */
public final class KeystoreFileCodec implements FileCodec {

    private static final String PREFIX = "INDRA1:";

    private final SecretCipher cipher;

    public KeystoreFileCodec(SecretCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public byte[] encode(byte[] plain) throws IOException {
        String encoded = Base64.encodeToString(plain, Base64.NO_WRAP);
        try {
            return (PREFIX + cipher.encrypt(encoded)).getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException error) {
            throw new IOException("Cannot encrypt local data", error);
        }
    }

    @Override
    public byte[] decode(byte[] stored) throws IOException {
        String text = new String(stored, StandardCharsets.UTF_8);
        if (!text.startsWith(PREFIX)) {
            return stored; // legacy plain JSON
        }
        try {
            String encoded = cipher.decrypt(text.substring(PREFIX.length()));
            return Base64.decode(encoded, Base64.NO_WRAP);
        } catch (RuntimeException error) {
            throw new IOException("Cannot decrypt local data (keystore key changed?)", error);
        }
    }

    /** Kept for callers that want to fail early when the Keystore is unusable. */
    public void selfTest() throws GeneralSecurityException {
        try {
            byte[] probe = "indra".getBytes(StandardCharsets.UTF_8);
            byte[] round = decode(encode(probe));
            if (!java.util.Arrays.equals(probe, round)) {
                throw new GeneralSecurityException("Keystore round-trip mismatch");
            }
        } catch (IOException error) {
            throw new GeneralSecurityException(error.getMessage(), error);
        }
    }
}
