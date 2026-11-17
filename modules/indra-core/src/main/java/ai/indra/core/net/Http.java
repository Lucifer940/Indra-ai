package ai.indra.core.net;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Small HTTP client that works everywhere INDRA runs.
 *
 * <p>INDRA Core is shared by the desktop app, the standalone backend and the
 * Android app. {@code java.net.http.HttpClient} only exists on Java 11+, so every
 * network call goes through this class instead: it is built on
 * {@link HttpURLConnection}, which is present on both the JDK and Android.</p>
 *
 * <p>Streaming calls (chat providers that answer with Server-Sent Events) use
 * {@link Response#stream()} so the caller can read the body as it arrives.</p>
 */
public final class Http {

    /** Default socket timeouts, in milliseconds. */
    public static final int DEFAULT_CONNECT_TIMEOUT = 15000;
    public static final int DEFAULT_READ_TIMEOUT = 120000;

    private Http() {
    }

    /** One HTTP response. Always {@link #close() closed} by the caller. */
    public static final class Response implements Closeable {

        private final HttpURLConnection connection;
        private final int status;
        private final InputStream stream;
        private String body;

        private Response(HttpURLConnection connection, int status, InputStream stream) {
            this.connection = connection;
            this.status = status;
            this.stream = stream;
        }

        public int statusCode() {
            return status;
        }

        public boolean isSuccessful() {
            return status >= 200 && status < 300;
        }

        public String header(String name) {
            return connection == null ? null : connection.getHeaderField(name);
        }

        /** Reads and caches the whole body as UTF-8 text. */
        public String body() throws IOException {
            if (body == null) {
                body = readAll(stream);
            }
            return body;
        }

        /** Raw body stream - used for streamed (SSE) responses. */
        public InputStream stream() {
            return stream;
        }

        @Override
        public void close() {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException ignored) {
                // nothing useful to do
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static Response get(String url, int readTimeoutMillis, String... headers) throws IOException {
        return send("GET", url, null, null, readTimeoutMillis, headers);
    }

    public static Response postJson(String url, String json, int readTimeoutMillis, String... headers)
            throws IOException {
        return send("POST", url, json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8),
                "application/json", readTimeoutMillis, headers);
    }

    public static Response postForm(String url, String form, int readTimeoutMillis, String... headers)
            throws IOException {
        return send("POST", url, form == null ? new byte[0] : form.getBytes(StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded", readTimeoutMillis, headers);
    }

    /**
     * Performs a request.
     *
     * @param headers name/value pairs; a null or empty value is skipped.
     */
    public static Response send(String method, String url, byte[] body, String contentType,
                               int readTimeoutMillis, String... headers) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
            connection.setReadTimeout(readTimeoutMillis <= 0 ? DEFAULT_READ_TIMEOUT : readTimeoutMillis);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "IndraAI/1.2.0 (+https://github.com/Lucifer940/Indra-ai)");
            if (headers != null) {
                for (int i = 0; i + 1 < headers.length; i += 2) {
                    if (headers[i] != null && headers[i + 1] != null && !headers[i + 1].isEmpty()) {
                        connection.setRequestProperty(headers[i], headers[i + 1]);
                    }
                }
            }
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type",
                        contentType == null ? "application/json" : contentType);
                connection.setFixedLengthStreamingMode(body.length);
                OutputStream out = connection.getOutputStream();
                try {
                    out.write(body);
                    out.flush();
                } finally {
                    out.close();
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Response(connection, status, stream);
        } catch (IOException error) {
            if (connection != null) {
                connection.disconnect();
            }
            throw error;
        } catch (RuntimeException error) {
            // Malformed URL and similar programming errors surface as IOExceptions.
            if (connection != null) {
                connection.disconnect();
            }
            throw new IOException("Cannot send " + method + " " + url + ": " + error.getMessage(), error);
        }
    }

    private static String readAll(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
