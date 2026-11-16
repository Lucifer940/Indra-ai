package ai.indra.backend.ai;

import ai.indra.core.apikeys.SecretCipher;
import ai.indra.core.security.Crypto;

import java.util.Base64;

/**
 * Encrypts stored provider keys with AES-256-GCM using a key derived from the
 * server master secret. Ciphertext is bound to the purpose string "apikey" so a
 * ciphertext cannot be replayed in another context.
 */
public final class AesSecretCipher implements SecretCipher {

    private static final byte[] SALT = "indra-ai-apikey-v1".getBytes(Crypto.UTF8);
    private static final byte[] AAD = "apikey".getBytes(Crypto.UTF8);
    private final byte[] key;

    public AesSecretCipher(String masterSecret) {
        if (masterSecret == null || masterSecret.length() < 32) {
            throw new IllegalArgumentException("Master secret must be at least 32 characters");
        }
        this.key = Crypto.pbkdf2(masterSecret.toCharArray(), SALT, 120000, 256);
    }

    @Override
    public String encrypt(String plaintext) {
        byte[] blob = Crypto.aesGcmEncrypt(key, plaintext.getBytes(Crypto.UTF8), AAD);
        return Base64.getEncoder().encodeToString(blob);
    }

    @Override
    public String decrypt(String cipherText) {
        byte[] blob = Base64.getDecoder().decode(cipherText);
        return new String(Crypto.aesGcmDecrypt(key, blob, AAD), Crypto.UTF8);
    }
}
