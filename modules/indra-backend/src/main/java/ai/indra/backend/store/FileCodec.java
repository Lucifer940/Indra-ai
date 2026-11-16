package ai.indra.backend.store;

import java.io.IOException;

/**
 * Optional at-rest transformation for the file store.
 *
 * <p>On Android the whole document store (accounts, memories, tasks, settings) is
 * encrypted with a key that never leaves the device's hardware-backed Keystore. On
 * a PC the default is no-op, so existing data directories keep working.</p>
 */
public interface FileCodec {

    /** Codec that stores plain JSON (desktop default). */
    FileCodec PLAIN = new FileCodec() {
        @Override
        public byte[] encode(byte[] plain) {
            return plain;
        }

        @Override
        public byte[] decode(byte[] stored) {
            return stored;
        }
    };

    byte[] encode(byte[] plain) throws IOException;

    byte[] decode(byte[] stored) throws IOException;
}
