package smoke;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

import com.relay.lastlab.server.LoopbackServer;
import com.relay.lastlab.server.Platform;

/**
 * End-to-end check of the in-app backend: real sockets, a real relay stand-in, real assertions.
 *
 * <p>Exits non-zero when any assertion fails so the build gate fails loudly.
 */
public final class SmokeMain {

    private static int checks = 0;
    private static final List<String> failures = new ArrayList<String>();

    public static void main(String[] args) throws Exception {
        // A catalog with the awkward cases the live relay actually exhibits: reasoning and
        // vision, a non-ASCII id, a model whose context_length is genuinely absent, plus
        // image- and video-output models.
        String catalog = buildCatalog();

        FakeRelay relay = new FakeRelay();
        try {
            relay.catalog(catalog);
            relay.stream(
                    // reasoning_content with no content, then reasoning under the other key,
                    // then content — all three shapes the live relay emits.
                    "{\"choices\":[{\"delta\":{\"reasoning_content\":\"Let me think.\"}}]}",
                    "{\"choices\":[{\"delta\":{\"reasoning\":\"Still thinking.\"}}]}",
                    "{\"choices\":[{\"delta\":{\"content\":\"Hello \"}}]}",
                    "{\"choices\":[{\"delta\":{\"content\":\"world\"}}],"
                            + "\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":7}}");

            MemPlatform platform = new MemPlatform();
            LoopbackServer server = new LoopbackServer(platform, relay.baseUrl());
            server.start();
            String base = server.getBaseUrl();

            try {
                testGatewayKeyRoundTrip(base);
                testHealth(base);
                testStaticAsset(base);
                testTraversalBlocked(base);
                testCatalogIsServedVerbatim(base);
                testNoKeyBlocksGeneration(base);
                testGenerationStreamsOverSubscriberConnection(base);
                testRelayAuthForwarded(relay);
            } finally {
                server.stop();
            }
        } finally {
            relay.close();
        }

        System.out.println();
        System.out.println("checks run: " + checks);
        if (failures.isEmpty()) {
            System.out.println("SMOKE PASS");
            System.exit(0);
        }
        System.out.println("SMOKE FAIL (" + failures.size() + ")");
        for (String f : failures) {
            System.out.println("  - " + f);
        }
        System.exit(1);
    }

    private static String buildCatalog() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"object\":\"list\",\"view\":\"canonical\",\"total\":4,\"merged_away\":1778,\"data\":[");
        sb.append(model("reasoning-vision-model", "Reasoning Vision Model", "1000000", "text", true));
        sb.append(',');
        sb.append(nonAsciiModel());
        sb.append(',');
        sb.append(model("no-context-model", "No Context Model", null, "image", false));
        sb.append(',');
        sb.append(videoModel());
        sb.append("]}");
        return sb.toString();
    }

    private static String nonAsciiModel() {
        return "{\"id\":\"deepseek-v4-1-flash-官方\",\"name\":\"DeepSeek 官方\","
                + "\"output_modalities\":[\"text\"],\"reasoning\":true,"
                + "\"relay\":{\"canonical\":\"deepseek-v4-1-flash-官方\",\"modality\":\"text\","
                + "\"context\":128000,\"input\":[\"text\"],\"output\":[\"text\"]}}";
    }

    private static String videoModel() {
        return "{\"id\":\"video-model\",\"name\":\"Video Model\",\"output_modalities\":[\"video\"],"
                + "\"relay\":{\"modality\":\"video\",\"input\":[\"text\"],\"output\":[\"video\"],"
                + "\"context\":500000}}";
    }

    private static String model(String id, String name, String context, String modality, boolean reasoning) {
        String topCtx = context == null ? "" : "\"context_length\":" + context + ",";
        String relCtx = context == null ? "" : "\"context\":" + context + ",";
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"," + topCtx
                + "\"owned_by\":\"test\",\"output_modalities\":[\"" + modality + "\"],"
                + "\"reasoning\":" + reasoning + ","
                + "\"relay\":{\"canonical\":\"" + id + "\",\"modality\":\"" + modality + "\"," + relCtx
                + "\"input\":[\"text\"],\"output\":[\"" + modality + "\"]}}";
    }

    // ------------------------------------------------------------------ checks

    private static void testGatewayKeyRoundTrip(String base) throws Exception {
        JSONObject none = getJson(base + "/api/gateway-key");
        check("no key before it is set", !none.optBoolean("configured"), none.toString());

        setKey(base, "sk-initial-key-1234");
        JSONObject got = getJson(base + "/api/gateway-key");
        check("key reported configured", got.optBoolean("configured"), got.toString());
        check("key is masked", got.optString("masked", "").contains("…"), got.toString());
        check("masked key hides the middle", !got.optString("masked", "").contains("initial"),
                got.optString("masked"));

        HttpURLConnection del = open(base + "/api/gateway-key", "DELETE");
        int delStatus = del.getResponseCode();
        readAll(del);
        del.disconnect();
        check("clearing a key succeeds", delStatus == 200, "status=" + delStatus);
        check("key cleared", !getJson(base + "/api/gateway-key").optBoolean("configured"),
                "still configured");

        HttpURLConnection bad = open(base + "/api/gateway-key", "POST");
        bad.setDoOutput(true);
        bad.setRequestProperty("Content-Type", "application/json");
        write(bad, "{\"key\":\"   \"}");
        check("empty key is rejected", bad.getResponseCode() == 400, "status=" + bad.getResponseCode());
        readAll(bad);
        bad.disconnect();
    }

    private static void testHealth(String base) throws Exception {
        JSONObject body = getJson(base + "/api/health");
        check("health status ok", "ok".equals(body.optString("status")), body.toString());
        setKey(base, "sk-health-check-1234");
        JSONObject withKey = getJson(base + "/api/health");
        check("health reflects a saved gateway key", withKey.optBoolean("gatewayConfigured"),
                "gatewayConfigured=" + withKey.optBoolean("gatewayConfigured"));
    }

    private static void testStaticAsset(String base) throws Exception {
        HttpURLConnection c = open(base + "/index.html");
        check("index.html served", c.getResponseCode() == 200, "status=" + c.getResponseCode());
        String text = new String(readAll(c), StandardCharsets.UTF_8);
        check("index.html is html", text.contains("<html"), "len=" + text.length());
        c.disconnect();

        // An unknown path with no extension falls back to the SPA shell so client routing works.
        HttpURLConnection deep = open(base + "/c/abc123");
        check("deep link falls back to index.html", deep.getResponseCode() == 200,
                "status=" + deep.getResponseCode());
        deep.disconnect();

        HttpURLConnection missing = open(base + "/assets/nope.js");
        check("missing asset is a 404", missing.getResponseCode() == 404,
                "status=" + missing.getResponseCode());
        readAll(missing);
        missing.disconnect();
    }

    private static void testTraversalBlocked(String base) throws Exception {
        HttpURLConnection c = open(base + "/%2e%2e%2f%2e%2e%2fAndroidManifest.xml");
        int status = c.getResponseCode();
        String body = new String(readAll(c), StandardCharsets.UTF_8);
        check("asset traversal blocked", status == 404 && !body.contains("package="),
                "status=" + status + " leaked=" + body.contains("package="));
        c.disconnect();
    }

    /**
     * The client reads {@code displaySetting} without a guard in several components, so an
     * absent or partial object crashes the chat page before anything is visible.
     */
    /**
     * The client disables every model control when there is no assistant, and the picker never
     * opens — so the app would be stuck with no way to choose a model.
     */
    /**
     * Choosing a model must survive the round-trip. The route used to answer 200 without writing
     * anything, so the picker showed no error and the selection silently vanished on reload.
     */
    private static void checkAssistantModelPersists(String base, JSONObject settings) throws Exception {
        String assistantId = settings.optString("assistantId", "");
        String modelId = settings.optJSONArray("providers").optJSONObject(0)
                .optJSONArray("models").optJSONObject(0).optString("id", "");
        JSONObject body = new JSONObject();
        body.put("assistantId", assistantId);
        body.put("modelId", modelId);

        JSONObject ack = postJson(base + "/api/settings/assistant/model", body.toString());
        check("model select is acknowledged", "ok".equals(ack.optString("status", "")), ack.toString());

        JSONObject after = getJson(base + "/api/settings");
        JSONObject assistant = null;
        JSONArray assistants = after.optJSONArray("assistants");
        for (int i = 0; i < assistants.length(); i++) {
            JSONObject a = assistants.optJSONObject(i);
            if (a != null && assistantId.equals(a.optString("id", ""))) {
                assistant = a;
                break;
            }
        }
        check("selected model is stored on the assistant",
                assistant != null && modelId.equals(assistant.optString("chatModelId", "")),
                "expected " + modelId + ", got "
                        + (assistant == null ? "no assistant" : assistant.optString("chatModelId", "")));

        // An unknown assistant must be reported, not silently ignored.
        JSONObject missing = new JSONObject();
        missing.put("assistantId", "does-not-exist");
        missing.put("modelId", modelId);
        JSONObject err = postJson(base + "/api/settings/assistant/model", missing.toString());
        JSONObject error = err.optJSONObject("error");
        check("unknown assistant is rejected",
                error != null && "no_such_assistant".equals(error.optString("code", "")),
                err.toString());
    }

    private static void checkAssistantPresent(JSONObject settings) {
        JSONArray assistants = settings.optJSONArray("assistants");
        check("settings ship an assistant",
                assistants != null && assistants.length() > 0,
                "assistants=" + (assistants == null ? "null" : String.valueOf(assistants.length())));
        if (assistants == null || assistants.length() == 0) {
            return;
        }
        String assistantId = settings.optString("assistantId", "");
        check("assistantId names a shipped assistant",
                assistants.optJSONObject(0).optString("id", "").equals(assistantId),
                "assistantId=" + assistantId);
        check("assistant carries a name",
                assistants.optJSONObject(0).optString("name", "").length() > 0,
                assistants.optJSONObject(0).toString());
    }

    private static void checkDisplaySetting(JSONObject settings) {
        JSONObject ds = settings.optJSONObject("displaySetting");
        check("settings expose displaySetting", ds != null, "missing displaySetting");
        if (ds == null) {
            return;
        }
        // Every key the client reads without a fallback.
        String[] required = {
            "userNickname", "showUserAvatar", "showModelName", "showTokenUsage",
            "showThinkingContent", "autoCloseThinking", "codeBlockAutoWrap",
            "codeBlockAutoCollapse", "showLineNumbers", "sendOnEnter", "enableAutoScroll",
            "fontSizeRatio", "pasteLongTextAsFile", "pasteLongTextThreshold",
        };
        StringBuilder missing = new StringBuilder();
        for (String key : required) {
            if (!ds.has(key)) {
                missing.append(key).append(' ');
            }
        }
        check("displaySetting carries every key the client reads",
                missing.length() == 0, "missing: " + missing.toString().trim());
        check("userNickname is non-empty",
                ds.optString("userNickname", "").trim().length() > 0, ds.toString());
        check("fontSizeRatio is a positive number",
                ds.optDouble("fontSizeRatio", 0.0) > 0.0, ds.toString());
    }

    /**
     * The catalog is fetched live and changes without notice, so the invariant is that the
     * served ids are exactly the relay's — never a hardcoded list.
     */
    private static void testCatalogIsServedVerbatim(String base) throws Exception {
        JSONObject settings = getJson(base + "/api/settings");
        JSONArray providers = settings.optJSONArray("providers");
        check("settings expose providers", providers != null, "missing providers");
        checkDisplaySetting(settings);
        checkAssistantPresent(settings);
        checkAssistantModelPersists(base, settings);
        if (providers == null || providers.length() != 1) {
            failures.add("expected exactly one relay provider, got "
                    + (providers == null ? "null" : String.valueOf(providers.length())));
            return;
        }
        JSONObject provider = providers.optJSONObject(0);
        check("provider is enabled", provider.optBoolean("enabled"), provider.toString());
        JSONArray models = provider.optJSONArray("models");
        check("provider carries models", models != null, "missing models");
        if (models == null) {
            return;
        }

        List<String> expectedIds = Arrays.asList(
                "reasoning-vision-model", "deepseek-v4-1-flash-官方", "no-context-model", "video-model");
        List<String> actualIds = new ArrayList<String>();
        for (int i = 0; i < models.length(); i++) {
            actualIds.add(models.getJSONObject(i).getString("id"));
        }
        check("served ids equal relay ids exactly", actualIds.equals(expectedIds),
                "expected=" + expectedIds + " actual=" + actualIds);
        check("non-ascii id preserved verbatim", actualIds.contains("deepseek-v4-1-flash-官方"),
                actualIds.toString());
        check("catalog not stale", !settings.optBoolean("catalogStale", true),
                "catalogStale=" + settings.optBoolean("catalogStale"));

        // A model whose `context_length` is absent must not gain an invented context window.
        JSONObject noContext = findById(models, "no-context-model");
        check("absent context is not invented",
                noContext != null && !noContext.has("contextWindowTokens"),
                noContext == null ? "missing" : noContext.toString());

        JSONObject withContext = findById(models, "reasoning-vision-model");
        check("context mapped when present",
                withContext != null && withContext.optInt("contextWindowTokens", -1) == 1000000,
                withContext == null ? "missing" : withContext.toString());

        JSONObject nonAscii = findById(models, "deepseek-v4-1-flash-官方");
        check("non-ascii id survives the JSON round trip",
                nonAscii != null && "deepseek-v4-1-flash-官方".equals(nonAscii.optString("modelId")),
                nonAscii == null ? "missing" : nonAscii.optString("modelId"));

        JSONObject video = findById(models, "video-model");
        check("video model uses a type the UI knows",
                video != null && isKnownModelType(video.optString("type")),
                video == null ? "missing" : String.valueOf(video.optString("type")));
        check("video model keeps its real output modality",
                video != null && video.optJSONArray("outputModalities").toString().contains("VIDEO"),
                video == null ? "missing" : video.toString());

        JSONObject image = findById(models, "no-context-model");
        check("image model classified as image",
                image != null && "IMAGE".equals(image.optString("type")),
                image == null ? "missing" : image.optString("type"));

        JSONObject reasoning = findById(models, "reasoning-vision-model");
        check("reasoning ability mapped",
                reasoning != null
                        && reasoning.optJSONArray("abilities").toString().contains("REASONING"),
                reasoning == null ? "missing" : String.valueOf(reasoning.opt("abilities")));

        // The picker filters on type === "CHAT", so anything typed outside the client's
        // ModelType union becomes unselectable. The video model carries no text output but is
        // still the only way to reach it, so it must stay selectable too.
        int chatModels = 0;
        for (int i = 0; i < models.length(); i++) {
            if ("CHAT".equals(models.optJSONObject(i).optString("type"))) {
                chatModels++;
            }
        }
        check("every reachable model stays selectable", chatModels == 3, "chatModels=" + chatModels);
    }

    private static void testNoKeyBlocksGeneration(String base) throws Exception {
        HttpURLConnection clear = open(base + "/api/gateway-key", "DELETE");
        clear.getResponseCode();
        readAll(clear);
        clear.disconnect();
        HttpURLConnection c = open(base + "/api/conversations/c1/messages", "POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        write(c, "{\"parts\":[{\"type\":\"text\",\"text\":\"hi\"}]}");
        int status = c.getResponseCode();
        String body = new String(readAll(c), StandardCharsets.UTF_8);
        c.disconnect();
        check("no key yields clear 401", status == 401 && body.contains("gateway_key_missing"),
                "status=" + status + " body=" + body);
        check("no key error is actionable", body.contains("Relay Gateway key"), body);
    }

    /**
     * The client keeps one SSE connection open per conversation and reads deltas from it; the
     * POST that starts generation returns only an acknowledgement.
     */
    private static void testGenerationStreamsOverSubscriberConnection(String base) throws Exception {
        setKey(base, "sk-test-key");

        JSONObject created = new JSONObject(raw(base, "POST", "/api/conversations", "{}"));
        String conversationId = created.optString("id", null);
        check("conversation created", conversationId != null && !conversationId.isEmpty(),
                "id=" + conversationId);
        if (conversationId == null) {
            return;
        }

        // Subscribe first, exactly as the client does, then send the message.
        StreamCapture capture = new StreamCapture(base, "/api/conversations/" + conversationId + "/stream");
        String ack = raw(base, "POST", "/api/conversations/" + conversationId + "/messages",
                "{\"parts\":[{\"type\":\"text\",\"text\":\"hi\"}],\"modelId\":\"auto\"}");
        check("message POST returns JSON acknowledgement", ack.trim().startsWith("{"), ack);
        check("acknowledgement is not an SSE stream",
                !ack.contains("data:") && !ack.contains("text/event-stream"), ack);

        List<JSONObject> events = capture.finish(20000);
        check("stream opened before generation and stayed open", events.size() > 1,
                "events=" + events.size());

        check("stream opens with a snapshot", hasEvent(events, "snapshot"), typesOf(events));

        List<JSONObject> updates = eventsOfType(events, "node_update");
        check("stream emits node_update", !updates.isEmpty(), typesOf(events));
        check("deltas are streamed progressively, not delivered once at the end",
                updates.size() >= 2, "node_updates=" + updates.size());

        String reasoning = assistantReasoning(updates);
        check("relay reasoning_content reaches the client",
                reasoning.contains("Let me think."), reasoning);
        check("relay reasoning key variant reaches the client",
                reasoning.contains("Still thinking."), reasoning);
        check("both reasoning channels are combined", reasoning.contains("Let me think.Still thinking."),
                reasoning);

        String text = assistantText(updates);
        check("relay content reaches the client", "Hello world".equals(text), text);

        check("generation is flagged while running", anyGenerating(updates), "no isGenerating=true seen");
        check("generation stops when finished", !lastEventGenerating(events),
                "last event isGenerating=" + flagOf(events));

        JSONObject last = updates.get(updates.size() - 1);
        check("token usage is reported from the relay",
                last.optJSONObject("node").toString().contains("promptTokens"),
                last.toString());

        // The finished turn must be readable over plain HTTP, not only over the live stream.
        JSONObject reloaded = getJson(base + "/api/conversations/" + conversationId);
        String persisted = assistantTextFromNodes(reloaded.optJSONArray("messages"));
        check("finished turn is persisted", "Hello world".equals(persisted), persisted);
        check("persisted turn is no longer generating",
                !reloaded.optBoolean("isGenerating", true), reloaded.toString());
    }

    private static void testRelayAuthForwarded(FakeRelay relay) {
        check("bearer token forwarded to relay",
                "Bearer sk-test-key".equals(relay.authorizationSeen()),
                "seen=" + relay.authorizationSeen());
    }

    // ---------------------------------------------------------------- helpers

    static void setKey(String base, String key) throws IOException {
        HttpURLConnection c = open(base + "/api/gateway-key", "POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        write(c, "{\"key\":\"" + key + "\"}");
        c.getResponseCode();
        readAll(c);
        c.disconnect();
    }

    private static boolean isKnownModelType(String type) {
        return "CHAT".equals(type) || "IMAGE".equals(type) || "EMBEDDING".equals(type);
    }

    private static boolean hasEvent(List<JSONObject> events, String type) {
        return !eventsOfType(events, type).isEmpty();
    }

    private static List<JSONObject> eventsOfType(List<JSONObject> events, String type) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (JSONObject e : events) {
            if (type.equals(e.optString("type"))) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * Concatenated text of the assistant messages in a stored conversation.
     *
     * <p>Only assistant messages are read: the turn includes the user's prompt, and asserting
     * against that concatenation would not prove the reply was stored.
     */
    static String assistantTextFromNodes(JSONArray nodes) {
        String text = "";
        if (nodes == null) {
            return text;
        }
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node == null) {
                continue;
            }
            JSONArray messages = node.optJSONArray("messages");
            if (messages == null) {
                continue;
            }
            for (int m = 0; m < messages.length(); m++) {
                JSONObject message = messages.optJSONObject(m);
                if (message == null || !"ASSISTANT".equals(message.optString("role"))) {
                    continue;
                }
                JSONArray parts = message.optJSONArray("parts");
                if (parts == null) {
                    continue;
                }
                for (int j = 0; j < parts.length(); j++) {
                    JSONObject part = parts.optJSONObject(j);
                    if (part != null && "text".equals(part.optString("type"))) {
                        text += part.optString("text", "");
                    }
                }
            }
        }
        return text;
    }

    /**
     * Runs the backend against the fake relay, for verification with a real client.
     *
     * <p>Nothing about this mode is special-cased in the server: it is the same backend the
     * assertions cover, held open so a browser can drive the actual shipped surface.
     */
    public static final class Serve {

        public static void main(String[] args) throws Exception {
            FakeRelay relay = new FakeRelay();
            relay.catalog("{\"data\":["
                    + "{\"id\":\"deepseek-v4-1-flash\",\"name\":\"DeepSeek V4.1 Flash\","
                    + "\"output_modalities\":[\"text\"],\"relay\":{\"modality\":\"text\",\"context\":128000}},"
                    + "{\"id\":\"claude-opus-5\",\"name\":\"Claude Opus 5\",\"output_modalities\":[\"text\"],"
                    + "\"abilities\":[\"reasoning\"],\"relay\":{\"modality\":\"text\",\"context\":200000}},"
                    + "{\"id\":\"gpt-5-5\",\"name\":\"GPT-5.5\",\"output_modalities\":[\"text\"],"
                    + "\"relay\":{\"modality\":\"text\",\"context\":400000}}]}");
            relay.stream(
                    "{\"choices\":[{\"delta\":{\"reasoning_content\":\"Checking the relay contract.\"}}]}",
                    "{\"choices\":[{\"delta\":{\"content\":\"Hello from LastLab.\"}}]}",
                    "{\"choices\":[{\"delta\":{\"content\":\" This reply was streamed.\"}}]}",
                    "{\"choices\":[{\"delta\":{}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":7}}");

            // The asset root mirrors the APK: LoopbackServer asks for "www/<path>", so the
            // platform's root is mobile/assets, not mobile/assets/www.
            Platform platform = new FilesystemPlatform(
                    new File("build/serve-store"),
                    new File("mobile/assets"));
            LoopbackServer server = new LoopbackServer(platform, relay.baseUrl());
            server.start();
            System.out.println("SERVE READY " + server.getBaseUrl());
            System.out.flush();

            final java.util.concurrent.CountDownLatch keepAlive = new java.util.concurrent.CountDownLatch(1);
            Thread anchor = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        keepAlive.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "serve-anchor");
            anchor.setDaemon(false);
            anchor.start();

            // The argument is in seconds, so a caller cannot silently stop the server after a
            // few milliseconds by passing what reads like a duration.
            Thread.sleep(Long.parseLong(args.length > 0 ? args[0] : "900") * 1000L);
            keepAlive.countDown();
            server.stop();
            relay.close();
        }
    }

    /**
     * A {@link Platform} backed by the real filesystem and the built SPA, matching the device:
     * assets come from {@code mobile/assets/www} and state from a directory on disk.
     *
     * <p>This is what a browser verification drives, so it must serve the same files the APK
     * packages rather than a stand-in.
     */
    static final class FilesystemPlatform implements Platform {

        private final File filesDir;
        private final File assetDir;
        private final Map<String, String> prefs = new LinkedHashMap<String, String>();

        FilesystemPlatform(File filesDir, File assetDir) {
            this.filesDir = filesDir;
            this.assetDir = assetDir;
        }

        @Override
        public File getFilesDir() {
            return filesDir;
        }

        @Override
        public String getPref(String key, String def) {
            String v = prefs.get(key);
            return v == null ? def : v;
        }

        @Override
        public void setPref(String key, String value) {
            if (value == null) {
                prefs.remove(key);
            } else {
                prefs.put(key, value);
            }
        }

        @Override
        public String readAsset(String path) {
            byte[] b = readAssetBytes(path);
            return b == null ? null : new String(b, StandardCharsets.UTF_8);
        }

        @Override
        public byte[] readAssetBytes(String path) {
            try {
                // Never let a request escape the asset root.
                File file = new File(assetDir, path);
                if (!file.getCanonicalPath().startsWith(assetDir.getCanonicalPath())) {
                    return null;
                }
                if (!file.isFile()) {
                    return null;
                }
                return java.nio.file.Files.readAllBytes(file.toPath());
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public List<String> listFiles(String dirRelative) {
            String[] names = new File(filesDir, dirRelative).list();
            List<String> out = new ArrayList<String>();
            if (names != null) {
                for (String name : names) {
                    out.add(name);
                }
            }
            return out;
        }

        @Override
        public void log(String tag, String msg, Throwable t) {
            if (t != null) {
                System.err.println("[log] " + tag + ": " + msg + " " + t);
            }
        }
    }

    private static String typesOf(List<JSONObject> events) {
        StringBuilder sb = new StringBuilder();
        for (JSONObject e : events) {
            sb.append(e.optString("type")).append(' ');
        }
        return sb.toString();
    }

    /** Concatenated text of the assistant message in the final node_update. */
    private static String assistantText(List<JSONObject> updates) {
        String text = "";
        for (JSONObject e : updates) {
            String t = partOf(e, "text", "text");
            if (t != null) {
                text = t;
            }
        }
        return text;
    }

    private static String assistantReasoning(List<JSONObject> updates) {
        String text = "";
        for (JSONObject e : updates) {
            String t = partOf(e, "reasoning", "reasoning");
            if (t != null) {
                text = t;
            }
        }
        return text;
    }

    private static String partOf(JSONObject event, String partType, String field) {
        JSONObject node = event.optJSONObject("node");
        if (node == null) {
            return null;
        }
        JSONArray messages = node.optJSONArray("messages");
        if (messages == null || messages.length() == 0) {
            return null;
        }
        JSONArray parts = messages.optJSONObject(0).optJSONArray("parts");
        if (parts == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject p = parts.optJSONObject(i);
            if (p != null && partType.equals(p.optString("type"))) {
                sb.append(p.optString(field, ""));
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static boolean anyGenerating(List<JSONObject> events) {
        for (JSONObject e : events) {
            if (e.optBoolean("isGenerating", false)) {
                return true;
            }
        }
        return false;
    }

    /** True when the most recent event still reports a running generation. */
    static boolean lastEventGenerating(List<JSONObject> events) {
        return !events.isEmpty() && events.get(events.size() - 1).optBoolean("isGenerating", false);
    }

    private static String flagOf(List<JSONObject> events) {
        return events.isEmpty() ? "no-events" : String.valueOf(lastEventGenerating(events));
    }

    private static JSONObject findById(JSONArray arr, String id) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) {
                return o;
            }
        }
        return null;
    }

    /**
     * Holds one SSE connection open, as a browser client does, and collects the events that
     * arrive until the stream reports the turn is finished.
     *
     * <p>The connection is deliberately never ended by the server, so reading to EOF would
     * hang; the stream is complete when generation reports it is no longer running.
     */
    static final class StreamCapture {
        private final java.net.Socket socket;
        private final String path;
        private final List<JSONObject> events = new ArrayList<JSONObject>();

        StreamCapture(String base, String path) throws Exception {
            this.path = path;
            int port = Integer.parseInt(base.substring(base.lastIndexOf(':') + 1));
            this.socket = new java.net.Socket("127.0.0.1", port);
            socket.setSoTimeout(15000);
            socket.setTcpNoDelay(true);
            OutputStream out = socket.getOutputStream();
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Accept: text/event-stream\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            // Give the subscription time to register before the POST lands.
            Thread.sleep(250);
        }

        List<JSONObject> finish(long timeoutMs) throws Exception {
            long deadline = System.currentTimeMillis() + timeoutMs;
            boolean dump = System.getenv("SMOKE_DUMP") != null;
            StringBuilder raw = new StringBuilder();
            byte[] buf = new byte[4096];
            InputStream in = socket.getInputStream();
            boolean sawGenerating = false;
            try {
                while (System.currentTimeMillis() < deadline) {
                    int n;
                    try {
                        n = in.read(buf);
                    } catch (java.net.SocketTimeoutException idle) {
                        break;
                    }
                    if (n == -1) {
                        if (dump) {
                            System.out.println("=== RAW STREAM (EOF) ===");
                            System.out.println(raw);
                            System.out.println("=== END RAW STREAM ===");
                        }
                        break;
                    }
                    raw.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                    events.clear();
                    // Re-parse the whole buffer: a single read can split an SSE event, and
                    // earlier events are still valid, so the flag must accumulate across reads.
                    sawGenerating |= parseEvents(dechunk(raw.toString()), events);
                    if (sawGenerating && !anyGenerating(events)) {
                        break;
                    }
                }
            } finally {
                socket.close();
            }
            return events;
        }

        /**
         * Undoes chunked transfer framing and parses each {@code data:} payload.
         *
         * @return true once a running generation has been observed
         */
        private static boolean parseEvents(String body, List<JSONObject> sink) {
            boolean sawGenerating = false;
            for (String line : body.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("data: ")) {
                    continue;
                }
                try {
                    JSONObject o = new JSONObject(trimmed.substring(6));
                    sink.add(o);
                    if (o.optBoolean("isGenerating", false)) {
                        sawGenerating = true;
                    }
                } catch (Exception ignored) {
                    // Comments and keep-alives share the stream; only JSON data lines matter.
                }
            }
            return sawGenerating;
        }

        private static String dechunk(String rawResponse) {
            int split = rawResponse.indexOf("\r\n\r\n");
            if (split < 0) {
                return "";
            }
            String head = rawResponse.substring(0, split);
            String body = rawResponse.substring(split + 4);
            if (!head.toLowerCase(Locale.US).contains("transfer-encoding: chunked")) {
                return body;
            }
            StringBuilder out = new StringBuilder();
            int at = 0;
            while (at < body.length()) {
                int eol = body.indexOf("\r\n", at);
                if (eol < 0) {
                    break;
                }
                String sizeLine = body.substring(at, eol).trim();
                int semi = sizeLine.indexOf(';');
                if (semi >= 0) {
                    sizeLine = sizeLine.substring(0, semi);
                }
                int size;
                try {
                    size = Integer.parseInt(sizeLine, 16);
                } catch (NumberFormatException e) {
                    break;
                }
                if (size == 0) {
                    break;
                }
                int start = eol + 2;
                int end = Math.min(body.length(), start + size);
                out.append(body, start, end);
                at = end + 2;
            }
            return out.toString();
        }
    }

    /** Performs a raw HTTP request and returns the decoded body. */
    static String raw(String base, String method, String path, String body) throws Exception {
        int port = Integer.parseInt(base.substring(base.lastIndexOf(':') + 1));
        java.net.Socket sock = new java.net.Socket("127.0.0.1", port);
        try {
            sock.setSoTimeout(25000);
            String payload = body == null ? "" : body;
            StringBuilder req = new StringBuilder();
            req.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
            req.append("Host: 127.0.0.1\r\n");
            req.append("Accept: application/json\r\n");
            if (body != null) {
                req.append("Content-Type: application/json\r\n");
                req.append("Content-Length: ").append(payload.length()).append("\r\n");
            }
            req.append("Connection: close\r\n\r\n").append(payload);
            OutputStream out = sock.getOutputStream();
            out.write(req.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();

            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            InputStream in = sock.getInputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            String rawResponse = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            int split = rawResponse.indexOf("\r\n\r\n");
            return split < 0 ? rawResponse : rawResponse.substring(split + 4);
        } finally {
            sock.close();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        return open(url, "GET");
    }

    private static HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    private static HttpURLConnection conn(String base, String path, String method) throws IOException {
        HttpURLConnection c = open(base + path, method);
        if (method.equals("DELETE")) {
            c.setDoOutput(true);
        }
        return c;
    }

    static JSONObject getJson(String url) throws Exception {
        HttpURLConnection conn = open(url);
        try {
            int status = conn.getResponseCode();
            String body = new String(readAll(conn), StandardCharsets.UTF_8);
            if (status != 200) {
                failures.add("GET " + url + " -> " + status + " " + body);
            }
            return new JSONObject(body);
        } finally {
            conn.disconnect();
        }
    }

    /** POSTs JSON and returns the decoded response, whatever its status. */
    static JSONObject postJson(String url, String body) throws Exception {
        HttpURLConnection conn = open(url, "POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try {
            write(conn, body);
            // Resolve the status first: readAll takes the error stream for a 4xx, which is only
            // populated once the response line has been parsed.
            conn.getResponseCode();
            String response = new String(readAll(conn), StandardCharsets.UTF_8);
            return new JSONObject(response);
        } finally {
            conn.disconnect();
        }
    }

    private static void write(HttpURLConnection conn, String body) throws IOException {
        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    private static byte[] readAll(HttpURLConnection conn) {
        try {
            InputStream in = conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                return new byte[0];
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static void check(String name, boolean ok, String detail) {
        checks++;
        System.out.println((ok ? "PASS  " : "FAIL  ") + name + (ok ? "" : "  [" + detail + "]"));
        if (!ok) {
            failures.add(name + "  [" + detail + "]");
        }
    }

    /** In-memory {@link Platform}; no Android, no filesystem. */
    static final class MemPlatform implements Platform {
        private final Map<String, String> prefs = new LinkedHashMap<String, String>();
        private final Map<String, byte[]> assets = new LinkedHashMap<String, byte[]>();

        MemPlatform() {
            assets.put("www/index.html",
                    "<!DOCTYPE html><html><head><title>LastLab</title></head><body></body></html>"
                            .getBytes(StandardCharsets.UTF_8));
        }

        void clearPref(String key) {
            prefs.remove(key);
        }

        @Override
        public String getPref(String key, String def) {
            String v = prefs.get(key);
            return v == null ? def : v;
        }

        @Override
        public void setPref(String key, String value) {
            if (value == null) {
                prefs.remove(key);
            } else {
                prefs.put(key, value);
            }
        }

        @Override
        public String readAsset(String path) {
            byte[] b = assets.get(path);
            return b == null ? null : new String(b, StandardCharsets.UTF_8);
        }

        @Override
        public byte[] readAssetBytes(String path) {
            return assets.get(path);
        }

        @Override
        public void log(String tag, String msg, Throwable t) {
            if (t != null || System.getenv("SMOKE_VERBOSE") != null) {
                System.out.println("[log] " + tag + ": " + msg + (t == null ? "" : " " + t));
            }
        }

        @Override
        public List<String> listFiles(String dirRelative) {
            return new ArrayList<String>();
        }

        @Override
        public File getFilesDir() {
            // Each run gets its own store so results never depend on a previous run.
            String base = System.getenv("SMOKE_TMPDIR");
            if (base == null || base.isEmpty()) {
                base = System.getProperty("java.io.tmpdir") + "/lastlab-smoke";
            }
            return new File(base);
        }
    }

    /** Tiny fixed-list helper so the test reads like the contract it checks. */
    private static final class Arrays {
        static List<String> asList(String... values) {
            List<String> out = new ArrayList<String>();
            for (String v : values) {
                out.add(v);
            }
            return out;
        }
    }
}