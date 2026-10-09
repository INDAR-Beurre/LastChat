package com.relay.lastlab.server;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Client for the Relay Gateway.
 *
 * <p>Generation uses a raw {@link Socket}/{@code SSLSocket} rather than
 * {@link HttpURLConnection}: the response is a chunked SSE stream that stays open for minutes,
 * and buffering it in full before delivering it would freeze the UI.
 */
public final class RelayClient {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 300000;

    private final String relayBaseUrl;
    private final Platform platform;
    private final GatewayKeyStore keyStore;
    private final Streams streams = new Streams();

    public RelayClient(String relayBaseUrl, GatewayKeyStore keyStore, Platform platform) {
        this.relayBaseUrl = stripTrailingSlash(relayBaseUrl);
        this.keyStore = keyStore;
        this.platform = platform;
    }

    public static String stripTrailingSlash(String s) {
        if (s == null) {
            return "";
        }
        String v = s.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private volatile String customRelayBaseUrl = null;

    public void setCustomRelayBaseUrl(String url) {
        this.customRelayBaseUrl = (url == null || url.trim().isEmpty()) ? null : stripTrailingSlash(url);
    }

    public String getEffectiveRelayBaseUrl() {
        return customRelayBaseUrl != null ? customRelayBaseUrl : relayBaseUrl;
    }

    public String getGatewayKey() {
        return keyStore.get();
    }

    public String getRelayBaseUrl() {
        return getEffectiveRelayBaseUrl();
    }

    public PingResult pingRelay(int timeoutMs) {
        long start = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(getEffectiveRelayBaseUrl() + "/v1/models").openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(Math.max(1000, timeoutMs));
            conn.setReadTimeout(Math.max(1000, timeoutMs));
            conn.setRequestProperty("Accept", "application/json");
            applyAuth(conn);
            int code = conn.getResponseCode();
            long elapsed = System.currentTimeMillis() - start;
            boolean ok = (code >= 200 && code < 400);
            return new PingResult(ok, elapsed, code, null);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return new PingResult(false, elapsed, -1, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static final class PingResult {
        public final boolean ok;
        public final long latencyMs;
        public final int statusCode;
        public final String error;
        public PingResult(boolean ok, long latencyMs, int statusCode, String error) {
            this.ok = ok;
            this.latencyMs = latencyMs;
            this.statusCode = statusCode;
            this.error = error;
        }
    }

    // ------------------------------------------------------------------ models

    /** Blocking fetch of the live catalog, or {@code null} when the relay is unreachable. */
    public JSONObject fetchModelsBlocking(int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(getEffectiveRelayBaseUrl() + "/v1/models").openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(Math.max(1000, timeoutMs));
            conn.setReadTimeout(Math.max(1000, timeoutMs));
            conn.setRequestProperty("Accept", "application/json");
            applyAuth(conn);
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                log("relay", "models fetch failed HTTP " + status, null);
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            java.io.InputStream in = conn.getInputStream();
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            } finally {
                in.close();
            }
            return new JSONObject(new String(out.toByteArray(), "UTF-8"));
        } catch (Exception e) {
            log("relay", "models fetch error", e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public interface ModelCallback {
        void onModels(JSONObject modelsOrNull);
    }

    public void fetchModels(final ModelCallback callback, final int timeoutMs) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                callback.onModels(fetchModelsBlocking(timeoutMs));
            }
        }, "relay-models");
        t.setDaemon(true);
        t.start();
    }

    // ----------------------------------------------------------------- streaming

    /** Receives SSE events as they arrive. */
    public interface StreamHandler {
        void onOpen(String requestId);

        /**
         * @param reasoning reasoning delta, or null/empty when the chunk carried none
         * @param content  visible content delta, or null/empty when the chunk carried none
         */
        void onDelta(String requestId, String reasoning, String content);

        void onDone(String requestId, Integer promptTokens, Integer completionTokens);

        void onError(String requestId, String message);
    }

    public void streamChat(final String requestId, final JSONObject body, final StreamHandler handler) {
        final AtomicBoolean cancelled = streams.register(requestId);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runStream(requestId, body, handler, cancelled);
                } catch (Exception e) {
                    handler.onError(requestId, describe(e));
                } finally {
                    streams.unregister(requestId);
                }
            }
        }, "relay-stream-" + requestId);
        t.setDaemon(true);
        t.start();
    }

    public void cancel(String requestId) {
        streams.cancel(requestId);
    }

    private void runStream(String requestId, JSONObject body, StreamHandler handler, AtomicBoolean cancelled)
            throws Exception {
        URL url = new URL(getEffectiveRelayBaseUrl() + "/v1/chat/completions");
        boolean https = "https".equalsIgnoreCase(url.getProtocol());
        int port = url.getPort() > 0 ? url.getPort() : (https ? 443 : 80);

        Socket sock = new Socket();
        try {
            sock.setTcpNoDelay(true);
            sock.setSoTimeout(READ_TIMEOUT_MS);
            sock.connect(new InetSocketAddress(url.getHost(), port), CONNECT_TIMEOUT_MS);
            if (https) {
                // Wrapping an already-connected socket upgrades it in place, so the streams
                // must be taken from `sock` only after the handshake completes.
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                sock = factory.createSocket(sock, url.getHost(), port, true);
                ((SSLSocket) sock).setUseClientMode(true);
                ((SSLSocket) sock).startHandshake();
            }
            streams.attach(requestId, sock);

            byte[] payload = body.toString().getBytes("UTF-8");
            StringBuilder head = new StringBuilder();
            head.append("POST /v1/chat/completions HTTP/1.1\r\n");
            head.append("Host: ").append(url.getHost()).append("\r\n");
            head.append("Accept: text/event-stream\r\n");
            head.append("Content-Type: application/json; charset=utf-8\r\n");
            head.append("Cache-Control: no-cache\r\n");
            head.append("Connection: close\r\n");
            head.append("Content-Length: ").append(payload.length).append("\r\n");
            String key = getGatewayKey();
            if (key != null && !key.isEmpty()) {
                head.append("Authorization: Bearer ").append(key).append("\r\n");
            }
            head.append("\r\n");

            OutputStream out = sock.getOutputStream();
            out.write(head.toString().getBytes("ISO-8859-1"));
            out.write(payload);
            out.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"), 8192);

            int status = readStatus(reader);
            if (status < 200 || status >= 300) {
                handler.onError(requestId, "Relay HTTP " + status + ": " + readErrorBody(reader));
                return;
            }

            handler.onOpen(requestId);

            Integer promptTokens = null;
            Integer completionTokens = null;
            StringBuilder data = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelled.get()) {
                    return;
                }
                if (line.isEmpty()) {
                    if (data.length() > 0) {
                        String chunk = data.toString();
                        data.setLength(0);
                        if ("[DONE]".equals(chunk.trim())) {
                            handler.onDone(requestId, promptTokens, completionTokens);
                            return;
                        }
                        Tokens usage = parseChunk(chunk, handler, requestId);
                        if (usage != null) {
                            promptTokens = usage.prompt;
                            completionTokens = usage.completion;
                        }
                    }
                    continue;
                }
                if (line.charAt(0) == ':') {
                    continue; // SSE comment / keep-alive
                }
                if (line.startsWith("data:")) {
                    String value = line.substring(5);
                    if (value.startsWith(" ")) {
                        value = value.substring(1);
                    }
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(value);
                }
                // `event:` and `id:` carry no per-chunk payload we need.
            }
            handler.onDone(requestId, promptTokens, completionTokens);
        } finally {
            streams.detach(requestId);
            try {
                sock.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    /**
     * Parses one SSE {@code data:} payload and dispatches any delta.
     *
     * <p>Reasoning arrives under two keys depending on the upstream path that served the
     * request: {@code delta.reasoning_content} on the OpenAI-compatible path and
     * {@code delta.reasoning} on the anthropic/gemini/responses paths. Both are accepted. A
     * chunk may legitimately carry reasoning with no content, so neither may be dereferenced
     * unconditionally. Usage may arrive as a sibling of {@code choices}.
     */
    private Tokens parseChunk(String chunk, StreamHandler handler, String requestId) {
        JSONObject obj;
        try {
            obj = new JSONObject(chunk);
        } catch (Exception e) {
            return null;
        }
        if (obj.has("error")) {
            handler.onError(requestId, errorMessage(obj.opt("error")));
            return null;
        }

        Integer prompt = null;
        Integer completion = null;
        JSONObject usage = obj.optJSONObject("usage");
        if (usage != null) {
            prompt = optInt(usage, "prompt_tokens");
            completion = optInt(usage, "completion_tokens");
            if (prompt == null) {
                prompt = optInt(usage, "input_tokens");
            }
            if (completion == null) {
                completion = optInt(usage, "output_tokens");
            }
        }

        JSONArray choices = obj.optJSONArray("choices");
        JSONObject first = choices == null ? null : choices.optJSONObject(0);
        if (first == null) {
            return usage != null ? new Tokens(prompt, completion) : null;
        }

        JSONObject delta = first.optJSONObject("delta");
        if (delta == null) {
            delta = first.optJSONObject("message");
        }

        String reasoning = null;
        String content = null;
        if (delta != null) {
            reasoning = firstNonEmpty(delta.optString("reasoning_content", null),
                    delta.optString("reasoning", null));
            content = nonEmpty(delta.optString("content", null));
        }
        if (reasoning != null || content != null) {
            handler.onDelta(requestId, reasoning, content);
        }
        return usage != null ? new Tokens(prompt, completion) : null;
    }

    private static String errorMessage(Object err) {
        if (err instanceof JSONObject) {
            return ((JSONObject) err).optString("message", "Relay error");
        }
        return err == null ? "Relay error" : String.valueOf(err);
    }

    private static String firstNonEmpty(String a, String b) {
        String first = nonEmpty(a);
        return first != null ? first : nonEmpty(b);
    }

    private static String nonEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static Integer optInt(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) {
            return null;
        }
        try {
            return Integer.valueOf(o.getInt(key));
        } catch (Exception e) {
            return null;
        }
    }

    private void applyAuth(HttpURLConnection conn) {
        String key = getGatewayKey();
        if (key != null && !key.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + key);
        }
    }

    private static int readStatus(BufferedReader reader) throws IOException {
        String statusLine = reader.readLine();
        while (statusLine != null && statusLine.isEmpty()) {
            statusLine = reader.readLine();
        }
        if (statusLine == null) {
            return -1;
        }
        int firstSpace = statusLine.indexOf(' ');
        if (firstSpace < 0) {
            return -1;
        }
        int secondSpace = statusLine.indexOf(' ', firstSpace + 1);
        String code = secondSpace < 0
                ? statusLine.substring(firstSpace + 1)
                : statusLine.substring(firstSpace + 1, secondSpace);
        try {
            return Integer.parseInt(code.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String readErrorBody(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null && line.length() > 0) {
            sb.append(line);
        }
        String body = sb.toString();
        if (body.startsWith("{")) {
            try {
                JSONObject err = new JSONObject(body).optJSONObject("error");
                if (err != null) {
                    return err.optString("message", body);
                }
            } catch (Exception ignored) {
                // fall through to the raw body
            }
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }

    private static String describe(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    void log(String tag, String msg, Throwable t) {
        if (platform != null) {
            platform.log(tag, msg, t);
        }
    }

    private static final class Tokens {
        final Integer prompt;
        final Integer completion;

        Tokens(Integer prompt, Integer completion) {
            this.prompt = prompt;
            this.completion = completion;
        }
    }

    /** Tracks in-flight requests so {@link #cancel(String)} can interrupt them. */
    private static final class Streams {
        private final Map<String, AtomicBoolean> active =
                new ConcurrentHashMap<String, AtomicBoolean>();
        private final Map<String, Socket> sockets = new ConcurrentHashMap<String, Socket>();

        AtomicBoolean register(String id) {
            AtomicBoolean flag = new AtomicBoolean(false);
            active.put(id, flag);
            return flag;
        }

        void attach(String id, Socket s) {
            if (active.containsKey(id)) {
                sockets.put(id, s);
            }
        }

        void detach(String id) {
            sockets.remove(id);
        }

        void unregister(String id) {
            active.remove(id);
            sockets.remove(id);
        }

        void cancel(String id) {
            AtomicBoolean flag = active.get(id);
            if (flag != null) {
                flag.set(true);
            }
            Socket s = sockets.remove(id);
            if (s != null) {
                try {
                    s.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }
}