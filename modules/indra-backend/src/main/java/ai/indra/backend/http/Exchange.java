package ai.indra.backend.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * One HTTP request/response exchange, independent of any platform HTTP server.
 *
 * <p>The same class serves the Windows/desktop backend and the embedded backend
 * inside the Android app, so routing, auth and streaming behave identically
 * everywhere.</p>
 */
public final class Exchange {

    /** Minimal request-URI view ({@code getPath()} / {@code getRawQuery()}). */
    public static final class RequestUri {
        private final String path;
        private final String rawQuery;

        RequestUri(String path, String rawQuery) {
            this.path = path;
            this.rawQuery = rawQuery;
        }

        public String getPath() {
            return path;
        }

        public String getRawQuery() {
            return rawQuery;
        }

        @Override
        public String toString() {
            return rawQuery == null ? path : path + "?" + rawQuery;
        }
    }

    private final String method;
    private final RequestUri uri;
    private final HeaderMap requestHeaders;
    private final InputStream requestBody;
    private final InetSocketAddress remoteAddress;
    private final OutputStream socketOut;
    private final Object writeLock;
    private final boolean clientWantsClose;

    private final HeaderMap responseHeaders = new HeaderMap();
    private boolean headersSent;
    private boolean chunked;
    private boolean finished;
    private boolean closeConnection;

    Exchange(String method, RequestUri uri, HeaderMap requestHeaders, InputStream requestBody,
             InetSocketAddress remoteAddress, OutputStream socketOut, Object writeLock,
             boolean clientWantsClose) {
        this.method = method;
        this.uri = uri;
        this.requestHeaders = requestHeaders;
        this.requestBody = requestBody;
        this.remoteAddress = remoteAddress;
        this.socketOut = socketOut;
        this.writeLock = writeLock;
        this.clientWantsClose = clientWantsClose;
    }

    public String getRequestMethod() {
        return method;
    }

    public RequestUri getRequestURI() {
        return uri;
    }

    public HeaderMap getRequestHeaders() {
        return requestHeaders;
    }

    public InputStream getRequestBody() {
        return requestBody == null ? new java.io.ByteArrayInputStream(new byte[0]) : requestBody;
    }

    public HeaderMap getResponseHeaders() {
        return responseHeaders;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public boolean isHeadersSent() {
        return headersSent;
    }

    /**
     * Writes the status line and headers.
     *
     * @param length {@code > 0} sends a fixed body, {@code 0} switches to chunked
     *               streaming (used by Server-Sent Events), {@code < 0} sends no body.
     */
    public void sendResponseHeaders(int status, long length) throws IOException {
        synchronized (writeLock) {
            if (headersSent) {
                return;
            }
            headersSent = true;
            StringBuilder head = new StringBuilder(512);
            head.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
            for (java.util.Map.Entry<String, String> entry : responseHeaders.asMap().entrySet()) {
                String name = entry.getKey();
                if (name == null || name.equalsIgnoreCase("Content-Length")
                        || name.equalsIgnoreCase("Transfer-Encoding")
                        || name.equalsIgnoreCase("Connection")) {
                    continue; // framing is written below
                }
                head.append(name).append(": ").append(entry.getValue()).append("\r\n");
            }
            if (length > 0) {
                head.append("Content-Length: ").append(length).append("\r\n");
            } else if (length == 0) {
                chunked = true;
                head.append("Transfer-Encoding: chunked\r\n");
            } else {
                head.append("Content-Length: 0\r\n");
            }
            boolean keepAlive = !clientWantsClose;
            head.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n");
            if (!keepAlive) {
                closeConnection = true;
            }
            head.append("\r\n");
            socketOut.write(head.toString().getBytes(StandardCharsets.UTF_8));
            socketOut.flush();
        }
    }

    /** Streams the body. Chunk framing is handled here. */
    public OutputStream getResponseBody() {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] bytes, int offset, int count) throws IOException {
                synchronized (writeLock) {
                    if (finished) {
                        return;
                    }
                    if (!headersSent) {
                        sendResponseHeaders(200, 0);
                    }
                    if (chunked) {
                        socketOut.write((Integer.toHexString(count) + "\r\n").getBytes(StandardCharsets.US_ASCII));
                        socketOut.write(bytes, offset, count);
                        socketOut.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                    } else {
                        socketOut.write(bytes, offset, count);
                    }
                }
            }

            @Override
            public void flush() throws IOException {
                synchronized (writeLock) {
                    socketOut.flush();
                }
            }

            @Override
            public void close() throws IOException {
                finish();
            }
        };
    }

    /** Completes the response (terminating chunk for streamed bodies). */
    public void finish() {
        synchronized (writeLock) {
            if (finished) {
                return;
            }
            finished = true;
            try {
                if (!headersSent) {
                    sendResponseHeaders(204, -1);
                }
                if (chunked) {
                    socketOut.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                }
                socketOut.flush();
            } catch (IOException error) {
                closeConnection = true;
            }
        }
    }

    /** Closes the exchange; the connection itself is finished by the server. */
    public void close() {
        finish();
    }

    /** True when the socket cannot be reused for another request. */
    public boolean shouldCloseConnection() {
        return closeConnection || clientWantsClose || finished && !headersSent;
    }

    private static String reason(int status) {
        switch (status) {
            case 200:
                return "OK";
            case 201:
                return "Created";
            case 204:
                return "No Content";
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 409:
                return "Conflict";
            case 413:
                return "Payload Too Large";
            case 422:
                return "Unprocessable Entity";
            case 429:
                return "Too Many Requests";
            case 500:
                return "Internal Server Error";
            case 502:
                return "Bad Gateway";
            case 503:
                return "Service Unavailable";
            default:
                return "Status";
        }
    }

    static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
