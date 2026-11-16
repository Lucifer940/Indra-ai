package ai.indra.core.security;

/**
 * Base64 codec. {@code java.util.Base64} is not available on Android before
 * API 26 in a usable form for all call sites, so a small implementation is used
 * in the shared core module.
 */
public final class Base64s {

    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final int[] INDEX = new int[128];

    static {
        for (int i = 0; i < INDEX.length; i++) {
            INDEX[i] = -1;
        }
        for (int i = 0; i < ALPHABET.length; i++) {
            INDEX[ALPHABET[i]] = i;
        }
    }

    private Base64s() {
    }

    public static String encode(byte[] data) {
        StringBuilder out = new StringBuilder(((data.length + 2) / 3) * 4);
        int i = 0;
        while (i < data.length) {
            int b0 = data[i++] & 0xFF;
            int b1 = i < data.length ? data[i++] & 0xFF : -1;
            int b2 = i < data.length ? data[i++] & 0xFF : -1;
            out.append(ALPHABET[b0 >> 2]);
            out.append(ALPHABET[((b0 & 0x03) << 4) | (b1 == -1 ? 0 : b1 >> 4)]);
            out.append(b1 == -1 ? '=' : ALPHABET[((b1 & 0x0F) << 2) | (b2 == -1 ? 0 : b2 >> 6)]);
            out.append(b2 == -1 ? '=' : ALPHABET[b2 & 0x3F]);
        }
        return out.toString();
    }

    public static byte[] decode(String text) {
        String value = text == null ? "" : text;
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '=') {
            end--;
        }
        byte[] out = new byte[(end * 3) / 4];
        int buffer = 0;
        int bits = 0;
        int position = 0;
        for (int i = 0; i < end; i++) {
            char c = value.charAt(i);
            int index = c < 128 ? INDEX[c] : -1;
            if (index < 0) {
                continue; // ignore whitespace / invalid characters
            }
            buffer = (buffer << 6) | index;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[position++] = (byte) ((buffer >> bits) & 0xFF);
            }
        }
        if (position == out.length) {
            return out;
        }
        byte[] trimmed = new byte[position];
        System.arraycopy(out, 0, trimmed, 0, position);
        return trimmed;
    }
}
