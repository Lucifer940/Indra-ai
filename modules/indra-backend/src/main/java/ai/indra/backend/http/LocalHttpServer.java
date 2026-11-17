package ai.indra.backend.http;

import ai.indra.core.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A small HTTP/1.1 server written in plain Java.
 *
 * <p>The desktop build used {@code com.sun.net.httpserver}, which does not exist on
 * Android. This implementation keeps the same routing, keep-alive and streaming
 * behaviour while depending only on {@link java.net.ServerSocket}, so the very same
 * backend can run inside the Android app on {@code 127.0.0.1}.</p>
 *
 * <p>Bind loopback only for the on-device backend: nothing outside the phone can
 * reach it.</p>
 */
public final class LocalHttpServer {

    /** Receives one decoded request at a time. */
    public interface RequestHandler {
        void handle(Exchange exchange);
    }

    private static final int MAX_HEADER_BYTES = 32 * 1024;
    private static final int MAX_REQUESTS_PER_CONNECTION = 200;

    private final String bindAddress;
    private final int requestedPort;
    private final RequestHandler handler;
    private final ExecutorService pool;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;
    private int boundPort;

    public LocalHttpServer(String bindAddress, int port, RequestHandler handler) {
        this.bindAddress = bindAddress == null || bindAddress.trim().isEmpty() ? "127.0.0.1" : bindAddress.trim();
        this.requestedPort = port;
        this.handler = handler;
        final AtomicInteger counter = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(16, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "indra-http-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName(bindAddress), requestedPort), 128);
        boundPort = serverSocket.getLocalPort();
        running = true;
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "indra-http-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        Log.info("LocalHttpServer", "Listening on " + bindAddress + ":" + boundPort);
    }

    /** Actual port, useful when the server was asked to pick a free one. */
    public int getPort() {
        return boundPort;
    }

    public boolean isRunning() {
        return running;
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // closing anyway
            }
            serverSocket = null;
        }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException error) {
                if (running) {
                    Log.warn("LocalHttpServer", "Accept failed: " + error.getMessage());
                }
                continue;
            }
            try {
                pool.execute(newConnectionTask(socket));
            } catch (RuntimeException error) {
                closeQuietly(socket);
            }
        }
    }

    private Runnable newConnectionTask(final Socket socket) {
        return new Runnable() {
            @Override
            public void run() {
                serve(socket);
            }
        };
    }

    private void serve(Socket socket) {
        final Object writeLock = new Object();
        try {
            socket.setSoTimeout(120000);
            socket.setTcpNoDelay(true);
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream(), 8192);
            OutputStream out = new BufferedOutputStream(socket.getOutputStream(), 8192);
            int served = 0;
            while (running && served < MAX_REQUESTS_PER_CONNECTION) {
                String requestLine = readLine(in, MAX_HEADER_BYTES);
                if (requestLine == null || requestLine.isEmpty()) {
                    return;
                }
                String[] parts = requestLine.split(" ");
                if (parts.length < 2) {
                    writeRawError(out, 400, "Bad Request");
                    return;
                }
                String method = parts[0].toUpperCase(java.util.Locale.ROOT);
                String target = parts[1];
                String version = parts.length > 2 ? parts[2] : "HTTP/1.1";

                HeaderMap headers = readHeaders(in);
                if (headers == null) {
                    writeRawError(out, 400, "Bad header block");
                    return;
                }

                String path = target;
                String query = null;
                int question = target.indexOf('?');
                if (question >= 0) {
                    path = target.substring(0, question);
                    query = target.substring(question + 1);
                }
                path = decode(path);

                InputStream body = bodyStream(in, headers);
                String connectionHeader = Exchange.lower(headers.getFirst("Connection"));
                boolean clientWantsClose = connectionHeader.contains("close")
                        || ("HTTP/1.0".equalsIgnoreCase(version) && !connectionHeader.contains("keep-alive"));

                InetSocketAddress remote = (InetSocketAddress) socket.getRemoteSocketAddress();
                Exchange exchange = new Exchange(method, new Exchange.RequestUri(path, query), headers, body,
                        remote, out, writeLock, clientWantsClose);
                try {
                    handler.handle(exchange);
                } catch (Throwable error) {
                    Log.error("LocalHttpServer", "Handler crashed for " + method + " " + path, error);
                    if (!exchange.isHeadersSent()) {
                        byte[] payload = "{\"error\":{\"code\":\"INTERNAL\",\"message\":\"Internal error\"}}"
                                .getBytes(StandardCharsets.UTF_8);
                        try {
                            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                            exchange.sendResponseHeaders(500, payload.length);
                            OutputStream stream = exchange.getResponseBody();
                            stream.write(payload);
                            stream.flush();
                        } catch (IOException writeError) {
                            return;
                        }
                    }
                } finally {
                    exchange.finish();
                }

                drain(body);
                if (exchange.shouldCloseConnection()) {
                    return;
                }
                served++;
            }
        } catch (EOFException ignored) {
            // client hung up
        } catch (IOException error) {
            Log.debug("LocalHttpServer", "Connection ended: " + error.getMessage());
        } catch (RuntimeException error) {
            Log.warn("LocalHttpServer", "Connection error: " + error.getMessage());
        } finally {
            closeQuietly(socket);
        }
    }

    private static void writeRawError(OutputStream out, int status, String message) {
        try {
            String payload = "{\"error\":{\"code\":\"BAD_REQUEST\",\"message\":\"" + message + "\"}}";
            String head = "HTTP/1.1 " + status + " Bad Request\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + payload.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.UTF_8));
            out.write(payload.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // socket already gone
        }
    }

    private static HeaderMap readHeaders(InputStream in) throws IOException {
        HeaderMap headers = new HeaderMap();
        int total = 0;
        while (true) {
            String line = readLine(in, MAX_HEADER_BYTES);
            if (line == null) {
                return null;
            }
            if (line.isEmpty()) {
                return headers;
            }
            total += line.length();
            if (total > MAX_HEADER_BYTES) {
                return null;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.add(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
    }

    private static InputStream bodyStream(final InputStream in, HeaderMap headers) {
        String transferEncoding = Exchange.lower(headers.getFirst("Transfer-Encoding"));
        if (transferEncoding.contains("chunked")) {
            return new ChunkedInputStream(in);
        }
        String lengthHeader = headers.getFirst("Content-Length");
        if (lengthHeader == null) {
            return new java.io.ByteArrayInputStream(new byte[0]);
        }
        long length;
        try {
            length = Long.parseLong(lengthHeader.trim());
        } catch (NumberFormatException error) {
            return new java.io.ByteArrayInputStream(new byte[0]);
        }
        return new LimitedInputStream(in, length);
    }

    private static void drain(InputStream body) {
        if (body == null) {
            return;
        }
        try {
            byte[] buffer = new byte[4096];
            int guard = 0;
            while (body.read(buffer) != -1 && guard++ < 4096) {
                // discard unread request bytes so the next request parses cleanly
            }
        } catch (IOException ignored) {
            // connection will be closed
        }
    }

    /** Reads one CRLF-terminated line; returns null at end of stream. */
    private static String readLine(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        int read;
        boolean any = false;
        while ((read = in.read()) != -1) {
            any = true;
            if (read == '\n') {
                break;
            }
            if (read != '\r') {
                buffer.write(read);
            }
            if (buffer.size() > limit) {
                throw new IOException("Header line too long");
            }
        }
        if (!any && buffer.size() == 0) {
            return null;
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Exception error) {
            return value;
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    /** Body stream bounded by a Content-Length header. */
    private static final class LimitedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        LimitedInputStream(InputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = delegate.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int count) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int limit = (int) Math.min(count, remaining);
            int read = delegate.read(buffer, offset, limit);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(delegate.available(), remaining);
        }
    }

    /** Decodes a chunked request body (rare, but keep-alive requires it). */
    private static final class ChunkedInputStream extends InputStream {
        private final InputStream delegate;
        private long remainingInChunk;
        private boolean finished;

        ChunkedInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int read = read(single, 0, 1);
            return read == -1 ? -1 : single[0] & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int count) throws IOException {
            if (finished) {
                return -1;
            }
            if (remainingInChunk <= 0) {
                String header = readLine(delegate, MAX_HEADER_BYTES);
                if (header == null) {
                    finished = true;
                    return -1;
                }
                header = header.trim();
                int semicolon = header.indexOf(';');
                if (semicolon >= 0) {
                    header = header.substring(0, semicolon);
                }
                long size;
                try {
                    size = Long.parseLong(header, 16);
                } catch (NumberFormatException error) {
                    finished = true;
                    return -1;
                }
                if (size == 0) {
                    readLine(delegate, MAX_HEADER_BYTES);
                    finished = true;
                    return -1;
                }
                remainingInChunk = size;
            }
            int limit = (int) Math.min(count, remainingInChunk);
            int read = delegate.read(buffer, offset, limit);
            if (read > 0) {
                remainingInChunk -= read;
            }
            return read;
        }
    }
}
