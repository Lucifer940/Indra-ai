package ai.indra.android.data;

import ai.indra.core.apikeys.SecretCipher;
import ai.indra.core.security.Base64s;
import ai.indra.core.util.Log;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM encryption backed by the Android Keystore.
 *
 * <p>The key never leaves the Keystore (hardware-backed on most devices), so a
 * copy of the app's preference file on another machine is useless.</p>
 */
public final class KeystoreCipher implements SecretCipher {

    private static final String PROVIDER = "AndroidKeyStore";
    private static final String ALIAS = "indra_master_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final String PREFIX = "v1:";

    private final Object lock = new Object();

    private SecretKey key() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(PROVIDER);
        keyStore.load(null);
        if (keyStore.containsAlias(ALIAS)) {
            return (SecretKey) keyStore.getKey(ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        synchronized (lock) {
            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.ENCRYPT_MODE, key());
                byte[] iv = cipher.getIV();
                byte[] encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));
                return PREFIX + Base64s.encode(iv) + ":" + Base64s.encode(encrypted);
            } catch (Exception error) {
                Log.error("KeystoreCipher", "Encryption failed", error);
                throw new IllegalStateException("Cannot encrypt with the Android Keystore", error);
            }
        }
    }

    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        if (!cipherText.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Not an INDRA encrypted value");
        }
        synchronized (lock) {
            try {
                String payload = cipherText.substring(PREFIX.length());
                int separator = payload.indexOf(':');
                if (separator <= 0) {
                    throw new IllegalArgumentException("Malformed encrypted value");
                }
                byte[] iv = Base64s.decode(payload.substring(0, separator));
                byte[] data = Base64s.decode(payload.substring(separator + 1));
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
                return new String(cipher.doFinal(data), "UTF-8");
            } catch (Exception error) {
                Log.error("KeystoreCipher", "Decryption failed", error);
                throw new IllegalStateException("Cannot decrypt with the Android Keystore", error);
            }
        }
    }
}
