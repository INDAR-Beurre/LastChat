package com.relay.lastlab.server;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONObject;

/**
 * Serves the bundled SPA and the {@code /api} surface to the app's own WebView.
 *
 * <p>The APK ships the SPA as files under {@code assets/www}. Those files cannot be loaded
 * over {@code file://} — the build emits root-absolute asset urls, which resolve to the device
 * root — so the app serves them over loopback HTTP and points the WebView at
 * {@code http://127.0.0.1:<port>/}. The server binds to loopback only and never leaves the
 * device.
 */
public final class LoopbackServer {

    private static final String ASSET_ROOT = "www";

    /** The bundler's output directory inside {@link #ASSET_ROOT}. */
    private static final String ASSET_PREFIX = "assets/";
    private static final int SOCKET_TIMEOUT_MS = 120000;

    private final Platform platform;
    private final ApiServer apiServer;
    private final RelayClient relayClient;
    private final GatewayKeyStore keyStore;

    private ServerSocket serverSocket;
    private ExecutorService workers;
    private final java.util.Set<Socket> openClients =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile int port;

    public LoopbackServer(Platform platform, String relayBaseUrl) throws IOException {
        this.platform = platform;
        this.keyStore = new GatewayKeyStore(platform);
        this.relayClient = new RelayClient(relayBaseUrl, keyStore, platform);
        String savedRelay = platform.getPref("relay_base_url", null);
        if (savedRelay != null && !savedRelay.trim().isEmpty()) {
            this.relayClient.setCustomRelayBaseUrl(savedRelay.trim());
        }
        this.apiServer = new ApiServer(platform, relayClient, this.keyStore);
    }

    public int getPort() {
        return port;
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + port;
    }

    public String getGatewayKey() {
        return keyStore.get();
    }

    public void setGatewayKey(String sk) {
        keyStore.set(sk);
    }

    public RelayClient getRelayClient() {
        return relayClient;
    }

    /** Binds the socket immediately so the port is known before {@link #start()} returns. */
    public int bind() throws IOException {
        if (serverSocket != null) {
            return port;
        }
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 64);
        port = serverSocket.getLocalPort();
        return port;
    }

    public void start() throws IOException {
        if (serverSocket == null) {
            bind();
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        workers = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "lastlab-http");
                t.setDaemon(true);
                return t;
            }
        });
        Thread acceptor = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "lastlab-accept");
        acceptor.setDaemon(true);
        acceptor.start();
        log("listening on " + getBaseUrl(), null);
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // best effort
        }
        // Closing the client sockets unblocks the workers parked on the live event streams.
        for (Socket client : openClients) {
            closeQuietly(client);
        }
        openClients.clear();
        if (workers != null) {
            workers.shutdownNow();
        }
        apiServer.dispose();
    }

    private void acceptLoop() {
        while (running.get() && serverSocket != null && !serverSocket.isClosed()) {
            final Socket client;
            try {
                client = serverSocket.accept();
            } catch (IOException e) {
                if (running.get()) {
                    log("accept failed: " + e.getMessage(), e);
                }
                continue;
            }
            try {
                workers.execute(new Runnable() {
                    @Override
                    public void run() {
                        serve(client);
                    }
                });
            } catch (RuntimeException rejected) {
                closeQuietly(client);
            }
        }
    }

    private void serve(Socket client) {
        try {
            client.setSoTimeout(SOCKET_TIMEOUT_MS);
            client.setTcpNoDelay(true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), Http.ISO_8859_1), 8192);
            OutputStream rawOut = new BufferedOutputStream(client.getOutputStream(), 16384);

            Request request = readRequest(in);
            if (request == null) {
                closeQuietly(client);
                return;
            }
            ResponseWriter responder = new ResponseWriter(rawOut, request.method.equals("HEAD"));
            try {
                serveRequest(request, responder);
                if (responder.streaming()) {
                    openClients.add(client);
                    parkWhileOpen(in, client);
                } else {
                    responder.finish();
                }
            } finally {
                openClients.remove(client);
            }
        } catch (IOException e) {
            // A dropped connection is normal once the client has what it needs.
            log("connection closed: " + e.getMessage(), null);
        } finally {
            closeQuietly(client);
        }
    }

    /**
     * Keeps a streaming response's connection alive until the client goes away.
     *
     * <p>Requests carry no trailing body, so the only event to wait for is the client's own
     * disconnect, which surfaces as end of stream.
     */
    private void parkWhileOpen(BufferedReader in, Socket client) {
        while (running.get() && !client.isClosed()) {
            try {
                if (in.read() == -1) {
                    return;
                }
            } catch (java.net.SocketTimeoutException idle) {
                // An idle stream is not a dead one; keep holding it open.
            } catch (IOException gone) {
                return;
            }
        }
    }

    private void serveRequest(Request request, ResponseWriter responder) throws IOException {
        String path = request.path;
        if (path.startsWith("/api/") || path.equals("/api")) {
            try {
                apiServer.handle(request.method, path, request.query, request.body, responder);
            } catch (Exception e) {
                log("api error " + request.method + " " + path, e);
                responder.json(500, errorJson("internal_error", describe(e)));
            }
            return;
        }
        serveStatic(path, responder);
    }

    /**
     * Serves a bundled asset. Unknown paths fall back to {@code index.html} so the SPA's
     * client-side router can handle deep links.
     */
    private void serveStatic(String path, ResponseWriter responder) throws IOException {
        String relative = normalise(path);
        byte[] bytes = null;
        if (relative != null) {
            bytes = readAssetBytes(ASSET_ROOT + "/" + relative);
        }
        // The route manifest lives inside the asset directory and names its chunks
        // "./assets/<file>", which the browser resolves against the manifest's own URL and so
        // requests "/assets/assets/<file>". Serving the bytes there would load the module — and
        // its React copy — a second time, so redirect to the canonical URL instead.
        if (bytes == null && relative != null && relative.startsWith(ASSET_PREFIX + ASSET_PREFIX)) {
            String canonical = relative.substring(ASSET_PREFIX.length());
            if (readAssetBytes(ASSET_ROOT + "/" + canonical) != null) {
                responder.redirect("/" + canonical);
                return;
            }
        }
        if (bytes == null && relative != null && (relative.endsWith(".woff") || relative.endsWith(".ttf"))) {
            int lastDot = relative.lastIndexOf('.');
            String woff2Rel = relative.substring(0, lastDot) + ".woff2";
            bytes = readAssetBytes(ASSET_ROOT + "/" + woff2Rel);
            if (bytes != null) {
                relative = woff2Rel;
            }
        }
        if (bytes == null && !hasExtension(path)) {
            relative = "index.html";
            bytes = readAssetBytes(ASSET_ROOT + "/" + relative);
        }
        if (bytes == null) {
            responder.json(404, errorJson("not_found", "No such asset: " + path));
            return;
        }
        responder.file(200, Http.mimeFor(relative), bytes);
    }

    /**
     * Maps a URL path to an asset path, or null when the request escapes the asset root.
     *
     * <p>Traversal is rejected rather than normalised away: a request must never reach outside
     * {@code assets/www}.
     */
    static String normalise(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String p = path;
        if (p.startsWith("/")) {
            p = p.substring(1);
        }
        int query = p.indexOf('?');
        if (query >= 0) {
            p = p.substring(0, query);
        }
        if (p.isEmpty() || p.equals("index.html")) {
            return "index.html";
        }
        if (p.contains("..") || p.contains("//") || p.contains("\\")) {
            return null;
        }
        if (p.indexOf(':') >= 0) {
            return null;
        }
        return p;
    }

    private static boolean hasExtension(String path) {
        int slash = path.lastIndexOf('/');
        String name = slash < 0 ? path : path.substring(slash + 1);
        return name.indexOf('.') >= 0;
    }

    private byte[] readAssetBytes(String assetPath) {
        try {
            return platform.readAssetBytes(assetPath);
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------ request model

    private static final class Request {
        String method = "GET";
        String path = "/";
        final Map<String, String> query = new java.util.LinkedHashMap<String, String>();
        String body = "";
    }

    private Request readRequest(BufferedReader in) throws IOException {
        String requestLine = in.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            return null;
        }
        Request req = new Request();
        req.method = parts[0].toUpperCase(java.util.Locale.US);
        String target = parts[1];

        int q = target.indexOf('?');
        if (q >= 0) {
            req.query.putAll(Http.parseQuery(target.substring(q + 1)));
            target = target.substring(0, q);
        }
        req.path = Http.urlDecode(target);

        Map<String, String> headers = new java.util.LinkedHashMap<String, String>();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim().toLowerCase(java.util.Locale.US);
            headers.put(name, line.substring(colon + 1).trim());
        }

        String contentType = headers.get("content-type");
        if (contentType != null) {
            // Reserved key: gives ApiServer the boundary for multipart parsing.
            req.query.put("__contentType", contentType);
        }

        // A request with neither Content-Length nor Transfer-Encoding has NO body. Reading
        // until EOF here would block forever, because the client is waiting for a response and
        // deliberately keeps the connection open.
        String length = headers.get("content-length");
        if (length != null || headers.get("transfer-encoding") != null) {
            req.body = Http.readBody(in, length);
        } else {
            req.body = "";
        }
        return req;
    }

    // ---------------------------------------------------------- response writer

    /**
     * Implements {@link ApiServer.Responder} over a real socket.
     *
     * <p>Ordinary responses carry a {@code Content-Length}. Streaming responses instead use
     * chunked transfer encoding: without either, an HTTP client cannot tell where the body
     * ends and would block or mis-parse the SSE events as a single payload.
     */
    static final class ResponseWriter implements ApiServer.Responder {
        private final OutputStream out;
        private final boolean headOnly;
        private boolean committed;
        private boolean chunked;
        private boolean dead;

        ResponseWriter(OutputStream out, boolean headOnly) {
            this.out = out;
            this.headOnly = headOnly;
        }

        boolean committed() {
            return committed;
        }

        /** True once this response became a live event stream. */
        boolean streaming() {
            return chunked;
        }

        private void writeHead(int status, String contentType, long length, boolean close) {
            StringBuilder head = new StringBuilder();
            head.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
            head.append("Content-Type: ").append(contentType).append("\r\n");
            head.append("Content-Length: ").append(length).append("\r\n");
            // The SPA and its worker run on the loopback origin, so a restrictive policy would
            // break them; loopback-only binding is the actual boundary.
            head.append("Access-Control-Allow-Origin: *\r\n");
            head.append("Cache-Control: no-store\r\n");
            if (close) {
                head.append("Connection: close\r\n");
            }
            head.append("\r\n");
            rawWrite(head.toString().getBytes(Http.ISO_8859_1));
        }

        /** Emits the streaming response head exactly once, before the first event. */
        private void beginStream() {
            if (committed || dead) {
                return;
            }
            StringBuilder head = new StringBuilder();
            head.append("HTTP/1.1 200 OK\r\n");
            head.append("Content-Type: text/event-stream; charset=utf-8\r\n");
            head.append("Cache-Control: no-cache, no-transform\r\n");
            head.append("X-Accel-Buffering: no\r\n");
            head.append("Transfer-Encoding: chunked\r\n");
            head.append("Access-Control-Allow-Origin: *\r\n");
            head.append("Connection: close\r\n");
            head.append("\r\n");
            rawWrite(head.toString().getBytes(Http.ISO_8859_1));
            chunked = true;
        }

        @Override
        public void json(int status, JSONObject body) {
            byte[] bytes = body.toString().getBytes(Http.UTF_8);
            writeHead(status, "application/json; charset=utf-8", bytes.length, true);
            if (!headOnly) {
                rawWrite(bytes);
            }
        }

        @Override
        public void text(int status, String contentType, String body) {
            byte[] bytes = body.getBytes(Http.UTF_8);
            writeHead(status, contentType, bytes.length, true);
            if (!headOnly) {
                rawWrite(bytes);
            }
        }

        @Override
        public boolean isDead() {
            return dead;
        }

        @Override
        public void file(int status, String mime, byte[] bytes) {
            writeHead(status, mime, bytes.length, true);
            if (!headOnly) {
                rawWrite(bytes);
            }
        }

        @Override
        public void redirect(String location) {
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 302 Found\r\n");
            sb.append("Location: ").append(location).append("\r\n");
            // The body is empty; a browser follows the Location without needing content.
            sb.append("Content-Length: 0\r\n");
            sb.append("Cache-Control: no-store\r\n");
            sb.append("Connection: close\r\n\r\n");
            rawWrite(sb.toString().getBytes(Http.ISO_8859_1));
        }

        @Override
        public void sse(String eventName, String json) {
            StringBuilder sb = new StringBuilder();
            if (eventName != null && !eventName.isEmpty()) {
                sb.append("event: ").append(eventName).append('\n');
            }
            sb.append("data: ").append(json).append("\n\n");
            writeChunk(sb.toString());
        }

        @Override
        public void sseComment(String text) {
            writeChunk(": " + text + "\n\n");
        }

        private void writeChunk(String s) {
            if (dead || headOnly) {
                return;
            }
            beginStream();
            byte[] bytes = s.getBytes(Http.UTF_8);
            // Chunk framing: hex length, CRLF, payload, CRLF.
            rawWrite((Integer.toHexString(bytes.length) + "\r\n").getBytes(Http.ISO_8859_1));
            rawWrite(bytes);
            rawWrite("\r\n".getBytes(Http.ISO_8859_1));
        }

        void finish() throws IOException {
            if (chunked && !dead) {
                // Terminating zero-length chunk.
                rawWrite("0\r\n\r\n".getBytes(Http.ISO_8859_1));
            }
            out.flush();
        }

        private void rawWrite(byte[] bytes) {
            if (dead) {
                return;
            }
            try {
                out.write(bytes);
                out.flush();
                committed = true;
            } catch (IOException e) {
                // The client hung up mid-stream; drop quietly instead of tearing down the
                // server thread that is still relaying on its behalf.
                dead = true;
            }
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
                case 405:
                    return "Method Not Allowed";
                case 409:
                    return "Conflict";
                case 413:
                    return "Payload Too Large";
                case 500:
                    return "Internal Server Error";
                case 502:
                    return "Bad Gateway";
                case 504:
                    return "Gateway Timeout";
                default:
                    return "Status";
            }
        }
    }

    static JSONObject errorJson(String code, String message) {
        JSONObject err = new JSONObject();
        try {
            err.put("message", message == null ? "" : message);
            err.put("type", code);
            err.put("code", code);
        } catch (Exception ignored) {
            // Literal strings cannot fail to serialise.
        }
        JSONObject root = new JSONObject();
        try {
            root.put("error", err);
            root.put("code", code);
            root.put("message", message == null ? "" : message);
        } catch (Exception ignored) {
            // as above
        }
        return root;
    }

    private static String describe(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
            // best effort
        }
    }

    private void log(String msg, Throwable t) {
        platform.log("lastlab", msg, t);
    }
}