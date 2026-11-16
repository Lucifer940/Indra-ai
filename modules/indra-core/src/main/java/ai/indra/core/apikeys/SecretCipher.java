package ai.indra.core.apikeys;

/**
 * Platform encryption for stored API keys: Android Keystore on phones,
 * DPAPI/keystore file on Windows, server master key on the backend.
 */
public interface SecretCipher {

    String encrypt(String plaintext);

    String decrypt(String cipherText);
}
