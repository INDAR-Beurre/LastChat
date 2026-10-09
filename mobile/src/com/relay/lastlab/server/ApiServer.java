package com.relay.lastlab.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Implements the {@code /api} surface the bundled SPA talks to.
 *
 * <p>This is the in-app backend: the APK ships no external server, so everything the client
 * needs — conversation storage, the live model catalog, and generation — is served from inside
 * the app over loopback.
 *
 * <p>The protocol is defined by {@code web-ui/app/**}, notably
 * {@code components/input/model-list.tsx} (catalog shape), {@code routes/conversations.tsx}
 * and {@code types/dto.ts} (stream events). Generation is acknowledged with JSON on the POST
 * and delivered over the conversation's own {@code /stream} connection.
 */
public final class ApiServer {

    /** The id of the assistant the app ships with. */
    private static final String DEFAULT_ASSISTANT_ID = "default";

    private static final long CATALOG_TTL_MS = 60000L;

    private final Platform platform;
    private final RelayClient relayClient;
    private final GatewayKeyStore keyStore;
    private final JsonStore store;

    private final Map<String, Object> settings = new ConcurrentHashMap<String, Object>();
    private final Map<String, Generation> generations = new ConcurrentHashMap<String, Generation>();
    private final List<Subscriber> conversationSubscribers =
            new CopyOnWriteArrayList<Subscriber>();
    private final List<Subscriber> listSubscribers = new CopyOnWriteArrayList<Subscriber>();
    private final List<Subscriber> settingsSubscribers = new CopyOnWriteArrayList<Subscriber>();
    private final AtomicLong idSeq = new AtomicLong();
    private final AtomicLong seq = new AtomicLong();

    private volatile JSONObject cachedCatalog;
    private volatile long catalogFetchedAt;

    public ApiServer(Platform platform, RelayClient relayClient, GatewayKeyStore keyStore) {
        this.platform = platform;
        this.relayClient = relayClient;
        this.keyStore = keyStore;
        this.store = new JsonStore(platform);
        seedSettings();
    }

    public void dispose() {
        conversationSubscribers.clear();
        listSubscribers.clear();
        settingsSubscribers.clear();
        generations.clear();
    }

    /** How the streaming surface writes to one connected client. */
    public interface Responder {
        void json(int status, JSONObject body);

        void text(int status, String contentType, String body);

        /** Emits one SSE event. A null name produces a bare {@code data:} event. */
        void sse(String eventName, String json);

        void sseComment(String text);

        void file(int status, String mime, byte[] bytes);

        /** Redirects to a canonical URL, keeping one URL — and one module instance — per asset. */
        void redirect(String location);

        /** True once the client has hung up, so this connection can be discarded. */
        boolean isDead();
    }

    // ------------------------------------------------------------------ routing

    public void handle(String method, String path, Map<String, String> query,
            String rawBody, Responder responder) throws Exception {
        if (path.equals("/api/health")) {
            JSONObject body = new JSONObject();
            put(body, "status", "ok");
            put(body, "version", "1.0.0");
            put(body, "gatewayConfigured", Boolean.valueOf(keyStore.isSet()));
            put(body, "relayUrl", relayClient.getRelayBaseUrl());
            put(body, "catalogTotal", Integer.valueOf(Models.idsOf(lastCatalog()).size()));
            responder.json(200, body);
            return;
        }
        if (path.equals("/api/connection/ping") || path.equals("/api/connection/test")) {
            handleConnectionPing(responder);
            return;
        }
        if (path.equals("/api/models/refresh") || path.equals("/api/catalog/refresh")) {
            handleRefreshModels(responder);
            return;
        }
        if (path.equals("/api/gateway-url") || path.equals("/api/settings/connection")) {
            handleGatewayUrl(method, rawBody, responder);
            return;
        }
        if (path.equals("/api/gateway-key")) {
            handleGatewayKey(method, rawBody, responder);
            return;
        }
        if (path.equals("/api/settings/stream")) {
            settingsSubscribers.add(new Subscriber(responder, settingsSubscribers));
            responder.sseComment("connected");
            responder.sse("settings", settingsPayload().toString());
            return;
        }
        if (path.equals("/api/settings") || path.startsWith("/api/settings/")) {
            handleSettings(method, path, query, rawBody, responder);
            return;
        }
        if (path.equals("/api/conversations")) {
            if (method.equals("POST")) {
                responder.json(200, newConversation());
                return;
            }
            responder.json(200, conversationList());
            return;
        }
        if (path.equals("/api/conversations/paged")) {
            responder.json(200, pagedConversations(query));
            return;
        }
        if (path.equals("/api/conversations/stream")) {
            listSubscribers.add(new Subscriber(responder, listSubscribers));
            responder.sseComment("connected");
            return;
        }
        if (path.startsWith("/api/conversations/")) {
            String rest = path.substring("/api/conversations/".length());
            int slash = rest.indexOf('/');
            String id = slash < 0 ? rest : rest.substring(0, slash);
            String tail = slash < 0 ? "" : rest.substring(slash + 1);
            handleConversation(method, id, tail, query, rawBody, responder);
            return;
        }
        responder.json(404, LoopbackServer.errorJson("not_found", "Unknown endpoint: " + path));
    }

    // -------------------------------------------------------------- connection & refresh

    private void handleConnectionPing(Responder responder) {
        RelayClient.PingResult res = relayClient.pingRelay(5000);
        JSONObject body = new JSONObject();
        put(body, "status", res.ok ? "ok" : "error");
        put(body, "connected", Boolean.valueOf(res.ok));
        put(body, "latencyMs", Long.valueOf(res.latencyMs));
        put(body, "statusCode", Integer.valueOf(res.statusCode));
        put(body, "gatewayConfigured", Boolean.valueOf(keyStore.isSet()));
        put(body, "relayUrl", relayClient.getRelayBaseUrl());
        if (res.error != null) {
            put(body, "error", res.error);
        }
        responder.json(200, body);
    }

    private void handleRefreshModels(Responder responder) {
        cachedCatalog = null;
        catalogFetchedAt = 0;
        JSONObject fresh = fetchCatalog();
        boolean hasLiveModels = (fresh != null && !Models.idsOf(fresh).isEmpty());
        if (hasLiveModels) {
            cachedCatalog = fresh;
            catalogFetchedAt = System.currentTimeMillis();
        }
        broadcastSettings();
        JSONObject effective = hasLiveModels ? fresh : (cachedCatalog != null ? cachedCatalog : Models.defaultFallbackCatalog());
        JSONObject out = new JSONObject();
        put(out, "status", "ok");
        put(out, "refreshed", Boolean.TRUE);
        put(out, "catalogTotal", Integer.valueOf(Models.idsOf(effective).size()));
        put(out, "catalogStale", Boolean.valueOf(!hasLiveModels));
        responder.json(200, out);
    }

    private void handleGatewayUrl(String method, String rawBody, Responder responder) throws Exception {
        if (method.equals("GET")) {
            JSONObject body = new JSONObject();
            put(body, "status", "ok");
            put(body, "relayUrl", relayClient.getEffectiveRelayBaseUrl());
            put(body, "defaultRelayUrl", relayClient.getRelayBaseUrl());
            responder.json(200, body);
            return;
        }
        if (method.equals("POST") || method.equals("PUT")) {
            JSONObject in = parseBody(rawBody);
            String url = in != null ? (in.has("relayUrl") ? in.optString("relayUrl", null) : in.optString("url", null)) : null;
            relayClient.setCustomRelayBaseUrl(url);
            platform.setPref("relay_base_url", url);
            cachedCatalog = null;
            catalogFetchedAt = 0;
            broadcastSettings();
            JSONObject body = new JSONObject();
            put(body, "status", "ok");
            put(body, "ok", Boolean.TRUE);
            put(body, "relayUrl", relayClient.getEffectiveRelayBaseUrl());
            responder.json(200, body);
            return;
        }
        responder.json(405, LoopbackServer.errorJson("method_not_allowed", method));
    }

    // -------------------------------------------------------------- gateway key

    private void handleGatewayKey(String method, String rawBody, Responder responder) throws Exception {
        if (method.equals("GET")) {
            JSONObject body = new JSONObject();
            put(body, "status", "ok");
            put(body, "configured", Boolean.valueOf(keyStore.isSet()));
            put(body, "masked", mask(keyStore.get()));
            responder.json(200, body);
            return;
        }
        if (method.equals("POST")) {
            JSONObject in = parseBody(rawBody);
            String key = in == null ? null : in.optString("key", null);
            if (key == null || key.trim().isEmpty()) {
                responder.json(400, LoopbackServer.errorJson("bad_request",
                        "Enter your Relay Gateway key."));
                return;
            }
            keyStore.set(key.trim());
            cachedCatalog = null;
            catalogFetchedAt = 0;
            broadcastSettings();
            JSONObject body = new JSONObject();
            put(body, "status", "ok");
            put(body, "ok", Boolean.TRUE);
            put(body, "masked", mask(keyStore.get()));
            responder.json(200, body);
            return;
        }
        if (method.equals("DELETE")) {
            keyStore.set(null);
            cachedCatalog = null;
            catalogFetchedAt = 0;
            broadcastSettings();
            JSONObject body = new JSONObject();
            put(body, "status", "ok");
            put(body, "ok", Boolean.TRUE);
            responder.json(200, body);
            return;
        }
        responder.json(405, LoopbackServer.errorJson("method_not_allowed", method));
    }

    static String mask(String key) {
        if (key == null || key.length() < 8) {
            return null;
        }
        return key.substring(0, 3) + "…" + key.substring(key.length() - 3);
    }

    // ----------------------------------------------------------------- settings

    private void handleSettings(String method, String path, Map<String, String> query,
            String rawBody, Responder responder) throws Exception {
        // Sub-resources that mutate one field.
        if (path.equals("/api/settings/assistant/model")) {
            if (!method.equals("POST") && !method.equals("PUT")) {
                responder.json(405, LoopbackServer.errorJson("method_not_allowed", method));
                return;
            }
            setAssistantModel(parseBody(rawBody), responder);
            return;
        }
        if (path.equals("/api/settings/favorite-models")) {
            if (!method.equals("POST") && !method.equals("PUT")) {
                responder.json(405, LoopbackServer.errorJson("method_not_allowed", method));
                return;
            }
            setFavoriteModels(parseBody(rawBody), responder);
            return;
        }
        if (path.equals("/api/settings/assistant")) {
            if (!method.equals("POST") && !method.equals("PUT")) {
                responder.json(405, LoopbackServer.errorJson("method_not_allowed", method));
                return;
            }
            setActiveAssistant(parseBody(rawBody), responder);
            return;
        }
        if (path.equals("/api/settings/search/enabled")) {
            if (!method.equals("POST") && !method.equals("PUT")) {
                responder.json(405, LoopbackServer.errorJson("method_not_allowed", method));
                return;
            }
            setSearchEnabled(parseBody(rawBody), responder);
            return;
        }
        if (path.equals("/api/settings/search/service")) {
            if (!method.equals("POST") && !method.equals("PUT")) {
                responder.json(405, LoopbackServer.errorJson("method_not_allowed", method));
                return;
            }
            setSearchService(parseBody(rawBody), responder);
            return;
        }
        if (path.equals("/api/settings/model/built-in-tool")) {
            if (!method.equals("POST") && !method.equals("PUT")) {
                responder.json(405, LoopbackServer.errorJson("method_not_allowed", method));
                return;
            }
            setModelBuiltInTool(parseBody(rawBody), responder);
            return;
        }
        if (path.equals("/api/settings/assistant/injections")) {
            if (!method.equals("POST") && !method.equals("PUT")) {
                responder.json(405, LoopbackServer.errorJson("method_not_allowed", method));
                return;
            }
            setAssistantInjections(parseBody(rawBody), responder);
            return;
        }
        if (path.equals("/api/settings/assistant/thinking-budget")) {
            if (!method.equals("POST") && !method.equals("PUT")) {
                responder.json(405, LoopbackServer.errorJson("method_not_allowed", method));
                return;
            }
            setAssistantThinkingBudget(parseBody(rawBody), responder);
            return;
        }
        if (path.equals("/api/settings/assistant/mcp")) {
            if (!method.equals("POST") && !method.equals("PUT")) {
                responder.json(405, LoopbackServer.errorJson("method_not_allowed", method));
                return;
            }
            setAssistantMcp(parseBody(rawBody), responder);
            return;
        }
        if (path.equals("/api/settings/refresh")) {
            handleRefreshModels(responder);
            return;
        }
        if (method.equals("GET")) {
            if (query != null && "true".equalsIgnoreCase(query.get("refresh"))) {
                cachedCatalog = null;
                catalogFetchedAt = 0;
            }
            responder.json(200, settingsPayload());
            return;
        }
        if (method.equals("PATCH") || method.equals("POST") || method.equals("PUT")) {
            JSONObject in = parseBody(rawBody);
            if (in != null) {
                applySettings(in);
            }
            broadcastSettings();
            responder.json(200, settingsPayload());
            return;
        }
        responder.json(405, LoopbackServer.errorJson("method_not_allowed", method));
    }

    private JSONObject findAssistant(String id) {
        Object list = settings.get("assistants");
        if (!(list instanceof JSONArray)) {
            return null;
        }
        JSONArray assistants = (JSONArray) list;
        for (int i = 0; i < assistants.length(); i++) {
            JSONObject a = assistants.optJSONObject(i);
            if (a != null && id.equals(a.optString("id", ""))) {
                return a;
            }
        }
        return null;
    }

    private void setFavoriteModels(JSONObject in, Responder responder) {
        if (in == null) {
            responder.json(400, LoopbackServer.errorJson("bad_request", "expected JSON"));
            return;
        }
        JSONArray list = in.optJSONArray("modelIds");
        if (list == null) {
            list = in.optJSONArray("favoriteModels");
        }
        if (list == null) {
            list = new JSONArray();
        }
        settings.put("favoriteModels", list);
        persistSettings();
        broadcastSettings();
        JSONObject out = new JSONObject();
        put(out, "status", "ok");
        put(out, "favoriteModels", list);
        responder.json(200, out);
    }

    private void setActiveAssistant(JSONObject in, Responder responder) {
        if (in == null) {
            responder.json(400, LoopbackServer.errorJson("bad_request", "expected JSON"));
            return;
        }
        String id = in.optString("assistantId", DEFAULT_ASSISTANT_ID);
        settings.put("assistantId", id);
        persistSettings();
        broadcastSettings();
        JSONObject out = new JSONObject();
        put(out, "status", "ok");
        put(out, "assistantId", id);
        responder.json(200, out);
    }

    private void setSearchEnabled(JSONObject in, Responder responder) {
        if (in == null) {
            responder.json(400, LoopbackServer.errorJson("bad_request", "expected JSON"));
            return;
        }
        boolean enabled = in.optBoolean("enabled", false);
        settings.put("enableWebSearch", Boolean.valueOf(enabled));
        persistSettings();
        broadcastSettings();
        JSONObject out = new JSONObject();
        put(out, "status", "ok");
        put(out, "enableWebSearch", Boolean.valueOf(enabled));
        responder.json(200, out);
    }

    private void setSearchService(JSONObject in, Responder responder) {
        if (in == null) {
            responder.json(400, LoopbackServer.errorJson("bad_request", "expected JSON"));
            return;
        }
        int index = in.optInt("index", 0);
        settings.put("searchServiceSelected", Integer.valueOf(index));
        persistSettings();
        broadcastSettings();
        JSONObject out = new JSONObject();
        put(out, "status", "ok");
        put(out, "searchServiceSelected", Integer.valueOf(index));
        responder.json(200, out);
    }

    private void setModelBuiltInTool(JSONObject in, Responder responder) {
        if (in == null) {
            responder.json(400, LoopbackServer.errorJson("bad_request", "expected JSON"));
            return;
        }
        String modelId = in.optString("modelId", "");
        String tool = in.optString("tool", "");
        boolean enabled = in.optBoolean("enabled", false);
        Object existing = settings.get("modelBuiltInTools");
        JSONObject toolConfigs = existing instanceof JSONObject ? (JSONObject) existing : new JSONObject();
        settings.put("modelBuiltInTools", toolConfigs);
        JSONObject modelTools = toolConfigs.optJSONObject(modelId);
        if (modelTools == null) {
            modelTools = new JSONObject();
            put(toolConfigs, modelId, modelTools);
        }
        put(modelTools, tool, Boolean.valueOf(enabled));
        persistSettings();
        broadcastSettings();
        JSONObject out = new JSONObject();
        put(out, "status", "ok");
        responder.json(200, out);
    }

    private void setAssistantInjections(JSONObject in, Responder responder) {
        if (in == null) {
            responder.json(400, LoopbackServer.errorJson("bad_request", "expected JSON"));
            return;
        }
        String assistantId = in.optString("assistantId", DEFAULT_ASSISTANT_ID);
        JSONObject assistant = findAssistant(assistantId);
        if (assistant == null) {
            responder.json(404, LoopbackServer.errorJson("no_such_assistant", assistantId));
            return;
        }
        if (in.has("modeInjectionIds")) {
            put(assistant, "modeInjectionIds", in.optJSONArray("modeInjectionIds"));
        }
        if (in.has("lorebookIds")) {
            put(assistant, "lorebookIds", in.optJSONArray("lorebookIds"));
        }
        persistSettings();
        broadcastSettings();
        JSONObject out = new JSONObject();
        put(out, "status", "ok");
        responder.json(200, out);
    }

    private void setAssistantThinkingBudget(JSONObject in, Responder responder) {
        if (in == null) {
            responder.json(400, LoopbackServer.errorJson("bad_request", "expected JSON"));
            return;
        }
        String assistantId = in.optString("assistantId", DEFAULT_ASSISTANT_ID);
        JSONObject assistant = findAssistant(assistantId);
        if (assistant == null) {
            responder.json(404, LoopbackServer.errorJson("no_such_assistant", assistantId));
            return;
        }
        int budget = in.optInt("thinkingBudget", 0);
        put(assistant, "thinkingBudget", Integer.valueOf(budget));
        persistSettings();
        broadcastSettings();
        JSONObject out = new JSONObject();
        put(out, "status", "ok");
        put(out, "thinkingBudget", Integer.valueOf(budget));
        responder.json(200, out);
    }

    private void setAssistantMcp(JSONObject in, Responder responder) {
        if (in == null) {
            responder.json(400, LoopbackServer.errorJson("bad_request", "expected JSON"));
            return;
        }
        String assistantId = in.optString("assistantId", DEFAULT_ASSISTANT_ID);
        JSONObject assistant = findAssistant(assistantId);
        if (assistant == null) {
            responder.json(404, LoopbackServer.errorJson("no_such_assistant", assistantId));
            return;
        }
        JSONArray mcp = in.optJSONArray("mcpServerIds");
        if (mcp != null) {
            put(assistant, "mcpServers", mcp);
        }
        persistSettings();
        broadcastSettings();
        JSONObject out = new JSONObject();
        put(out, "status", "ok");
        responder.json(200, out);
    }

    /**
     * Sets the chat model of one assistant.
     */
    private void setAssistantModel(JSONObject in, Responder responder) {
        if (in == null) {
            responder.json(400, LoopbackServer.errorJson("bad_request", "expected a JSON object"));
            return;
        }
        String assistantId = in.optString("assistantId", "");
        String modelId = in.optString("modelId", "");
        JSONObject assistant = findAssistant(assistantId);
        if (assistant == null) {
            responder.json(404, LoopbackServer.errorJson("no_such_assistant", assistantId));
            return;
        }
        put(assistant, "chatModelId", modelId);
        settings.put("chatModelId", modelId);
        persistSettings();
        broadcastSettings();
        JSONObject out = new JSONObject();
        put(out, "status", "ok");
        responder.json(200, out);
    }

    /**
     * Applies a settings patch.
     */
    private void applySettings(JSONObject in) {
        for (Iterator<String> it = in.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject value = in.optJSONObject(k);
            if (value == null) {
                settings.put(k, in.opt(k));
            } else {
                Object existing = settings.get(k);
                JSONObject merged = existing instanceof JSONObject
                        ? (JSONObject) existing
                        : new JSONObject();
                copyOnto(merged, value);
                settings.put(k, merged);
            }
        }
        persistSettings();
    }

    /**
     * Builds the {@code Settings} payload, folding in the live relay catalog.
     *
     * <p>A failed fetch is reported through {@code catalogStale}/{@code catalogError} rather
     * than hidden, so the client can tell a real catalog from a cached one instead of showing
     * stale models as if they were live.
     */
    JSONObject settingsPayload() {
        JSONObject out = new JSONObject();
        for (Map.Entry<String, Object> e : settings.entrySet()) {
            put(out, e.getKey(), e.getValue());
        }

        JSONObject catalog = fetchCatalog();
        boolean hasLive = (catalog != null && !Models.idsOf(catalog).isEmpty());
        boolean stale = !hasLive;
        if (hasLive) {
            cachedCatalog = catalog;
            catalogFetchedAt = System.currentTimeMillis();
        } else if (cachedCatalog != null && !Models.idsOf(cachedCatalog).isEmpty()) {
            catalog = cachedCatalog;
        } else {
            catalog = Models.defaultFallbackCatalog();
        }

        put(out, "gatewayConfigured", Boolean.valueOf(keyStore.isSet()));
        put(out, "gatewayKeyMasked", mask(keyStore.get()));
        put(out, "providers", providers(catalog));
        put(out, "displaySetting", displaySetting(out));
        put(out, "catalogStale", Boolean.valueOf(stale));
        if (stale) {
            put(out, "catalogError",
                    (cachedCatalog != null && !Models.idsOf(cachedCatalog).isEmpty())
                            ? "Showing the last catalog that loaded; the relay is unreachable."
                            : "Model catalog using fallback baseline; relay gateway is unreachable.");
            put(out, "catalogTotal", Integer.valueOf(Models.idsOf(catalog).size()));
        } else {
            put(out, "catalogError", JSONObject.NULL);
            put(out, "catalogTotal", Integer.valueOf(Models.idsOf(catalog).size()));
        }
        return out;
    }

    /**
     * Returns the client's display settings, filled in with defaults.
     *
     * <p>The client types {@code displaySetting} as required and several of its components read
     * it without a guard, so an absent or partial object crashes the chat page on first render.
     * Every key the client reads is therefore always present here.
     */
    private JSONObject displaySetting(JSONObject out) {
        Object existing = out.opt("displaySetting");
        JSONObject ds = existing instanceof JSONObject ? (JSONObject) existing : new JSONObject();

        // Defaults mirror the forked app's own DisplaySetting, so an untouched install renders
        // the way the upstream UI does rather than in a half-configured state.
        defaultBoolean(ds, "showUserAvatar", true);
        defaultBoolean(ds, "showModelIcon", true);
        defaultBoolean(ds, "showModelName", true);
        defaultBoolean(ds, "showAssistantBubbles", false);
        defaultBoolean(ds, "showTokenUsage", true);
        defaultBoolean(ds, "showContextTokenSummary", false);
        defaultBoolean(ds, "showThinkingContent", true);
        defaultBoolean(ds, "autoCloseThinking", false);
        defaultBoolean(ds, "codeBlockAutoWrap", true);
        defaultBoolean(ds, "codeBlockAutoCollapse", false);
        defaultBoolean(ds, "showLineNumbers", false);
        defaultBoolean(ds, "sendOnEnter", true);
        defaultBoolean(ds, "enableAutoScroll", true);
        defaultBoolean(ds, "pasteLongTextAsFile", false);
        defaultBoolean(ds, "showMessageJumper", false);
        defaultBoolean(ds, "messageJumperOnLeft", false);
        defaultBoolean(ds, "showContextStacks", true);
        defaultBoolean(ds, "newChatShowAvatar", false);
        defaultNumber(ds, "fontSizeRatio", 1.0);
        defaultNumber(ds, "pasteLongTextThreshold", 2000);
        defaultString(ds, "userNickname", "User");
        return ds;
    }

    private static void defaultBoolean(JSONObject o, String key, boolean value) {
        if (!o.has(key)) {
            put(o, key, Boolean.valueOf(value));
        }
    }

    private static void defaultNumber(JSONObject o, String key, double value) {
        if (!o.has(key)) {
            put(o, key, Double.valueOf(value));
        }
    }

    private static void defaultString(JSONObject o, String key, String value) {
        if (!o.has(key) || o.isNull(key)) {
            put(o, key, value);
        }
    }

    /**
     * Exposes the catalog the way the client reads it: one enabled provider named after the
     * gateway, holding every model the relay reported.
     */
    private JSONArray providers(JSONObject catalog) {
        JSONArray providers = new JSONArray();
        JSONObject provider = new JSONObject();
        put(provider, "id", "relay");
        put(provider, "enabled", Boolean.TRUE);
        put(provider, "name", "Relay Gateway");
        put(provider, "type", "OPENAI");
        JSONObject effective = catalog != null ? catalog : (cachedCatalog != null ? cachedCatalog : Models.defaultFallbackCatalog());
        put(provider, "models", Models.toProviderModels(effective));
        providers.put(provider);
        return providers;
    }

    private JSONObject fetchCatalog() {
        long age = System.currentTimeMillis() - catalogFetchedAt;
        if (cachedCatalog != null && age < CATALOG_TTL_MS) {
            return cachedCatalog;
        }
        return relayClient.fetchModelsBlocking(8000);
    }

    private void broadcastSettings() {
        if (settingsSubscribers.isEmpty()) {
            return;
        }
        String payload = settingsPayload().toString();
        for (Subscriber s : settingsSubscribers) {
            s.send("settings", payload);
        }
    }

    // ------------------------------------------------------------ conversations

    private JSONObject newConversation() {
        String id = "c" + Long.toHexString(System.currentTimeMillis())
                + "-" + Long.toHexString(idSeq.getAndIncrement());
        JSONObject conversation = new JSONObject();
        put(conversation, "id", id);
        put(conversation, "assistantId", DEFAULT_ASSISTANT_ID);
        put(conversation, "title", "");
        put(conversation, "enabledSkillIds", new JSONArray());
        put(conversation, "truncateIndex", Integer.valueOf(-1));
        put(conversation, "chatSuggestions", new JSONArray());
        put(conversation, "isPinned", Boolean.FALSE);
        put(conversation, "createAt", Long.valueOf(System.currentTimeMillis()));
        put(conversation, "updateAt", Long.valueOf(System.currentTimeMillis()));
        put(conversation, "isGenerating", Boolean.FALSE);
        put(conversation, "isFork", Boolean.FALSE);
        put(conversation, "isConsolidated", Boolean.FALSE);
        put(conversation, "contextSummaryUpToIndex", Integer.valueOf(0));
        put(conversation, "lastPruneTime", Long.valueOf(0));
        put(conversation, "lastPruneMessageCount", Integer.valueOf(0));
        put(conversation, "lastRefreshTime", Long.valueOf(0));
        put(conversation, "messages", new JSONArray());

        persist(id, conversation);
        broadcastListInvalidate("default");
        return conversation;
    }

    private void handleConversation(String method, String id, String tail, Map<String, String> query,
            String rawBody, Responder responder) throws Exception {
        JSONObject conversation = store.read(conversationFile(id));
        if (conversation == null) {
            conversation = new JSONObject();
            put(conversation, "id", id);
            put(conversation, "assistantId", DEFAULT_ASSISTANT_ID);
            put(conversation, "title", "");
            put(conversation, "enabledSkillIds", new JSONArray());
            put(conversation, "chatSuggestions", new JSONArray());
            put(conversation, "truncateIndex", Integer.valueOf(-1));
            put(conversation, "isPinned", Boolean.FALSE);
            put(conversation, "isFork", Boolean.FALSE);
            put(conversation, "isConsolidated", Boolean.FALSE);
            put(conversation, "contextSummaryUpToIndex", Integer.valueOf(0));
            put(conversation, "lastPruneTime", Long.valueOf(0));
            put(conversation, "lastPruneMessageCount", Integer.valueOf(0));
            put(conversation, "lastRefreshTime", Long.valueOf(0));
            put(conversation, "createAt", Long.valueOf(System.currentTimeMillis()));
            put(conversation, "updateAt", Long.valueOf(System.currentTimeMillis()));
            put(conversation, "isGenerating", Boolean.FALSE);
            put(conversation, "messages", new JSONArray());
        }

        if (tail.equals("stream")) {
            serveConversationStream(id, conversation, responder);
            return;
        }
        if (tail.isEmpty()) {
            if (method.equals("GET")) {
                responder.json(200, conversation);
                return;
            }
            if (method.equals("DELETE")) {
                store.delete(conversationFile(id));
                generations.remove(id);
                broadcastListInvalidate(conversation.optString("assistantId", DEFAULT_ASSISTANT_ID));
                JSONObject out = new JSONObject();
                put(out, "status", "ok");
                responder.json(200, out);
                return;
            }
            if (method.equals("PATCH") || method.equals("PUT")) {
                JSONObject in = parseBody(rawBody);
                if (in != null) {
                    copyOnto(conversation, in);
                    persist(id, conversation);
                }
                responder.json(200, conversation);
                return;
            }
            responder.json(405, LoopbackServer.errorJson("method_not_allowed", method));
            return;
        }
        if (tail.equals("messages") && method.equals("POST")) {
            if (!keyStore.isSet()) {
                responder.json(401, LoopbackServer.errorJson("gateway_key_missing",
                        "Add your Relay Gateway key in Settings before sending a message."));
                return;
            }
            JSONObject ack = startGeneration(id, conversation, rawBody);
            responder.json(200, ack);
            return;
        }
        if (tail.equals("stop") && method.equals("POST")) {
            stopGeneration(id);
            responder.json(200, status("stopped"));
            return;
        }
        if (tail.equals("title") && method.equals("POST")) {
            JSONObject in = parseBody(rawBody);
            if (in != null) {
                put(conversation, "title", in.optString("title", ""));
            }
            persist(id, conversation);
            broadcastListInvalidate(conversation.optString("assistantId", DEFAULT_ASSISTANT_ID));
            responder.json(200, status("ok"));
            return;
        }
        if (tail.equals("regenerate-title") && method.equals("POST")) {
            String title = generateConversationTitle(conversation);
            put(conversation, "title", title);
            persist(id, conversation);
            broadcastListInvalidate(conversation.optString("assistantId", DEFAULT_ASSISTANT_ID));
            JSONObject out = new JSONObject();
            put(out, "status", "ok");
            put(out, "title", title);
            responder.json(200, out);
            return;
        }
        if (tail.equals("pin") && method.equals("POST")) {
            JSONObject in = parseBody(rawBody);
            boolean newPin;
            if (in != null && in.has("isPinned")) {
                newPin = in.optBoolean("isPinned", false);
            } else {
                newPin = !conversation.optBoolean("isPinned", false);
            }
            put(conversation, "isPinned", Boolean.valueOf(newPin));
            persist(id, conversation);
            broadcastListInvalidate(conversation.optString("assistantId", DEFAULT_ASSISTANT_ID));
            JSONObject out = new JSONObject();
            put(out, "status", "ok");
            put(out, "isPinned", Boolean.valueOf(newPin));
            responder.json(200, out);
            return;
        }
        if (tail.equals("move") && method.equals("POST")) {
            JSONObject in = parseBody(rawBody);
            String target = in != null ? in.optString("assistantId", DEFAULT_ASSISTANT_ID) : DEFAULT_ASSISTANT_ID;
            put(conversation, "assistantId", target);
            persist(id, conversation);
            broadcastListInvalidate(target);
            JSONObject out = new JSONObject();
            put(out, "status", "ok");
            put(out, "assistantId", target);
            responder.json(200, out);
            return;
        }
        if (tail.equals("context-refresh") && method.equals("POST")) {
            JSONObject in = parseBody(rawBody);
            JSONArray msgs = conversation.optJSONArray("messages");
            int count = (in != null && in.has("summaryUpToIndex"))
                    ? in.optInt("summaryUpToIndex", 0)
                    : (msgs != null ? msgs.length() : 0);
            put(conversation, "contextSummaryUpToIndex", Integer.valueOf(count));
            put(conversation, "lastRefreshTime", Long.valueOf(System.currentTimeMillis()));
            persist(id, conversation);
            JSONObject out = new JSONObject();
            put(out, "status", "ok");
            put(out, "success", Boolean.TRUE);
            put(out, "contextSummaryUpToIndex", Integer.valueOf(count));
            put(out, "summary", "Context refreshed (" + count + " turns)");
            put(out, "messagesSummarized", Integer.valueOf(count));
            put(out, "tokensSaved", Integer.valueOf(count * 60));
            put(out, "error", JSONObject.NULL);
            responder.json(200, out);
            return;
        }
        if (tail.equals("skills") && method.equals("POST")) {
            JSONObject in = parseBody(rawBody);
            JSONArray skills = in != null ? (in.has("enabledSkillIds") ? in.optJSONArray("enabledSkillIds") : in.optJSONArray("skillIds")) : null;
            if (skills != null) {
                put(conversation, "enabledSkillIds", skills);
                persist(id, conversation);
            }
            JSONObject out = new JSONObject();
            put(out, "status", "ok");
            put(out, "enabledSkillIds", conversation.optJSONArray("enabledSkillIds"));
            responder.json(200, out);
            return;
        }
        if (tail.equals("fork") && method.equals("POST")) {
            JSONObject in = parseBody(rawBody);
            String fromMsgId = in != null ? (in.has("messageId") ? in.optString("messageId", null) : in.optString("nodeId", null)) : null;
            int nodeIndex = in != null ? in.optInt("nodeIndex", -1) : -1;
            String newId = "c" + Long.toHexString(System.currentTimeMillis())
                    + "-" + Long.toHexString(idSeq.getAndIncrement());
            JSONObject forked = forkConversation(conversation, newId, fromMsgId, nodeIndex);
            persist(newId, forked);
            broadcastListInvalidate(forked.optString("assistantId", DEFAULT_ASSISTANT_ID));
            JSONObject out = new JSONObject();
            put(out, "status", "ok");
            put(out, "conversationId", newId);
            put(out, "forkConversationId", newId);
            responder.json(200, out);
            return;
        }
        if (tail.startsWith("nodes/") && tail.endsWith("/select") && method.equals("POST")) {
            String mid = tail.substring("nodes/".length(), tail.length() - "/select".length());
            JSONObject in = parseBody(rawBody);
            int selectIdx = in != null ? in.optInt("selectIndex", 0) : 0;
            selectNodeBranch(id, conversation, mid, selectIdx, responder);
            return;
        }
        if (tail.startsWith("messages/") && method.equals("DELETE")) {
            String msgId = tail.substring("messages/".length());
            deleteMessageFromConversation(id, conversation, msgId, responder);
            return;
        }
        if (tail.equals("regenerate") && method.equals("POST")) {
            JSONObject in = parseBody(rawBody);
            String msgId = in != null ? in.optString("messageId", null) : null;
            regenerateConversationTurn(id, conversation, msgId, responder);
            return;
        }
        if (tail.equals("tool-approval") && method.equals("POST")) {
            responder.json(200, status("ok"));
            return;
        }
        responder.json(404, LoopbackServer.errorJson("not_found",
                "Unknown conversation path: /" + tail));
    }

    private static String generateConversationTitle(JSONObject conversation) {
        JSONArray nodes = conversation.optJSONArray("messages");
        if (nodes != null && nodes.length() > 0) {
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject n = nodes.optJSONObject(i);
                if (n == null) continue;
                JSONArray msgs = n.optJSONArray("messages");
                if (msgs == null) continue;
                for (int m = 0; m < msgs.length(); m++) {
                    JSONObject msg = msgs.optJSONObject(m);
                    if (msg != null && "USER".equalsIgnoreCase(msg.optString("role", ""))) {
                        JSONArray parts = msg.optJSONArray("parts");
                        if (parts != null && parts.length() > 0) {
                            for (int p = 0; p < parts.length(); p++) {
                                JSONObject part = parts.optJSONObject(p);
                                if (part != null && "text".equals(part.optString("type", ""))) {
                                    String text = part.optString("text", "").trim();
                                    if (!text.isEmpty()) {
                                        return text.length() > 28 ? text.substring(0, 28) + "..." : text;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return "Conversation";
    }

    private JSONObject forkConversation(JSONObject source, String newId, String messageId, int nodeIndex) {
        JSONObject forked = new JSONObject();
        put(forked, "id", newId);
        put(forked, "assistantId", source.optString("assistantId", DEFAULT_ASSISTANT_ID));
        String title = source.optString("title", "");
        put(forked, "title", title.isEmpty() ? "Forked Chat" : title + " (Fork)");
        put(forked, "enabledSkillIds", source.optJSONArray("enabledSkillIds"));
        put(forked, "chatSuggestions", new JSONArray());
        put(forked, "truncateIndex", Integer.valueOf(-1));
        put(forked, "isPinned", Boolean.FALSE);
        put(forked, "isFork", Boolean.TRUE);
        put(forked, "isConsolidated", Boolean.FALSE);
        put(forked, "contextSummaryUpToIndex", Integer.valueOf(0));
        put(forked, "lastPruneTime", Long.valueOf(0));
        put(forked, "lastPruneMessageCount", Integer.valueOf(0));
        put(forked, "lastRefreshTime", Long.valueOf(0));
        put(forked, "createAt", Long.valueOf(System.currentTimeMillis()));
        put(forked, "updateAt", Long.valueOf(System.currentTimeMillis()));
        put(forked, "isGenerating", Boolean.FALSE);

        JSONArray sourceNodes = source.optJSONArray("messages");
        JSONArray newNodes = new JSONArray();
        if (sourceNodes != null) {
            boolean stop = false;
            for (int i = 0; i < sourceNodes.length() && !stop; i++) {
                if (nodeIndex >= 0 && i > nodeIndex) {
                    break;
                }
                JSONObject n = sourceNodes.optJSONObject(i);
                if (n == null) continue;
                newNodes.put(n);
                if (nodeIndex >= 0 && i == nodeIndex) {
                    break;
                }
                if (messageId != null) {
                    JSONArray msgs = n.optJSONArray("messages");
                    if (msgs != null) {
                        for (int m = 0; m < msgs.length(); m++) {
                            JSONObject msg = msgs.optJSONObject(m);
                            if (msg != null && messageId.equals(msg.optString("id", ""))) {
                                stop = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        put(forked, "messages", newNodes);
        return forked;
    }

    private void selectNodeBranch(String conversationId, JSONObject conversation, String nodeId,
            int selectIndex, Responder responder) {
        JSONArray nodes = conversation.optJSONArray("messages");
        if (nodes != null) {
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject node = nodes.optJSONObject(i);
                if (node != null && nodeId.equals(node.optString("id", ""))) {
                    put(node, "selectIndex", Integer.valueOf(selectIndex));
                    persist(conversationId, conversation);
                    broadcastNode(conversationId, nodeId, i, node, false, System.currentTimeMillis());
                    JSONObject out = new JSONObject();
                    put(out, "status", "ok");
                    responder.json(200, out);
                    return;
                }
            }
        }
        responder.json(404, LoopbackServer.errorJson("not_found", "node not found"));
    }

    private void deleteMessageFromConversation(String conversationId, JSONObject conversation,
            String messageId, Responder responder) {
        JSONArray nodes = conversation.optJSONArray("messages");
        if (nodes == null) {
            responder.json(404, LoopbackServer.errorJson("not_found", "message not found"));
            return;
        }
        boolean found = false;
        JSONArray newNodes = new JSONArray();
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node == null) continue;
            JSONArray msgs = node.optJSONArray("messages");
            if (msgs == null) {
                newNodes.put(node);
                continue;
            }
            boolean nodeContains = false;
            JSONArray newMsgs = new JSONArray();
            for (int m = 0; m < msgs.length(); m++) {
                JSONObject msg = msgs.optJSONObject(m);
                if (msg != null && messageId.equals(msg.optString("id", ""))) {
                    nodeContains = true;
                    found = true;
                } else if (msg != null) {
                    newMsgs.put(msg);
                }
            }
            if (!nodeContains) {
                newNodes.put(node);
            } else if (newMsgs.length() > 0) {
                put(node, "messages", newMsgs);
                int select = node.optInt("selectIndex", 0);
                if (select >= newMsgs.length()) {
                    put(node, "selectIndex", Integer.valueOf(newMsgs.length() - 1));
                }
                newNodes.put(node);
            }
        }
        if (!found) {
            responder.json(404, LoopbackServer.errorJson("not_found", "message not found"));
            return;
        }
        put(conversation, "messages", newNodes);
        persist(conversationId, conversation);
        broadcastListInvalidate(conversation.optString("assistantId", DEFAULT_ASSISTANT_ID));
        JSONObject out = new JSONObject();
        put(out, "status", "ok");
        responder.json(200, out);
    }

    private void regenerateConversationTurn(String conversationId, JSONObject conversation,
            String messageId, Responder responder) throws Exception {
        JSONArray nodes = conversation.optJSONArray("messages");
        if (nodes == null || nodes.length() == 0) {
            responder.json(400, LoopbackServer.errorJson("bad_request", "empty conversation"));
            return;
        }
        int targetIndex = nodes.length() - 1;
        if (messageId != null && !messageId.isEmpty()) {
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject n = nodes.optJSONObject(i);
                if (n == null) continue;
                JSONArray msgs = n.optJSONArray("messages");
                if (msgs != null) {
                    for (int m = 0; m < msgs.length(); m++) {
                        JSONObject msg = msgs.optJSONObject(m);
                        if (msg != null && messageId.equals(msg.optString("id", ""))) {
                            targetIndex = i;
                            break;
                        }
                    }
                }
            }
        }
        JSONObject targetNode = nodes.optJSONObject(targetIndex);
        if (targetNode == null) {
            responder.json(400, LoopbackServer.errorJson("bad_request", "node not found"));
            return;
        }

        String newMsgId = messageId();
        String modelId = currentModelId();
        final Generation gen = new Generation(conversationId, targetNode.optString("id", messageId()),
                modelId, conversation, true);
        JSONArray msgs = targetNode.optJSONArray("messages");
        if (msgs == null) {
            msgs = new JSONArray();
            put(targetNode, "messages", msgs);
        }
        msgs.put(newMessage(newMsgId, "ASSISTANT", new JSONArray(), gen.modelId));
        put(targetNode, "selectIndex", Integer.valueOf(msgs.length() - 1));

        put(conversation, "isGenerating", Boolean.TRUE);
        persist(conversationId, conversation);
        broadcastNode(conversationId, gen.nodeId, targetIndex, targetNode, true, System.currentTimeMillis());

        generations.put(conversationId, gen);

        JSONArray history = new JSONArray();
        for (int i = 0; i < targetIndex; i++) {
            JSONObject n = nodes.optJSONObject(i);
            if (n == null) continue;
            JSONArray ms = n.optJSONArray("messages");
            if (ms == null) continue;
            for (int m = 0; m < ms.length(); m++) {
                JSONObject msg = ms.optJSONObject(m);
                if (msg != null) history.put(relayMessage(msg));
            }
        }

        JSONObject body = new JSONObject();
        put(body, "model", gen.modelId);
        put(body, "messages", history);
        put(body, "stream", Boolean.TRUE);

        final int finalNodeIndex = targetIndex;
        relayClient.streamChat(gen.nodeId, body, new RelayClient.StreamHandler() {
            @Override public void onOpen(String r) {}
            @Override public void onDelta(String r, String reasoning, String content) {
                if (gen.finished) return;
                synchronized (gen) {
                    if (reasoning != null) gen.reasoning.append(reasoning);
                    if (content != null) gen.content.append(content);
                }
                publishPartial(gen, finalNodeIndex, System.currentTimeMillis());
            }
            @Override public void onDone(String r, Integer p, Integer c) {
                finishGeneration(gen, finalNodeIndex, p, c);
            }
            @Override public void onError(String r, String message) {
                gen.finished = true;
                generations.remove(gen.conversationId);
                put(gen.conversation, "isGenerating", Boolean.FALSE);
                persist(gen.conversationId, gen.conversation);
                broadcastError(gen.conversationId, message);
                broadcastListInvalidate(gen.conversation.optString("assistantId", DEFAULT_ASSISTANT_ID));
            }
        });

        JSONObject ack = status("accepted");
        put(ack, "conversationId", conversationId);
        put(ack, "nodeId", gen.nodeId);
        responder.json(200, ack);
    }

    private static String conversationFile(String id) {
        return "conversation-" + id + ".json";
    }

    private void persist(String id, JSONObject conversation) {
        put(conversation, "updateAt", Long.valueOf(System.currentTimeMillis()));
        store.write(conversationFile(id), conversation);
    }

    private JSONObject status(String s) {
        JSONObject o = new JSONObject();
        put(o, "status", s);
        return o;
    }

    private JSONObject conversationList() {
        JSONArray items = new JSONArray();
        for (String name : store.listNames()) {
            if (!name.startsWith("conversation-") || !name.endsWith(".json")) {
                continue;
            }
            JSONObject c = store.read(name);
            if (c != null) {
                items.put(summaryOf(c));
            }
        }
        JSONObject out = new JSONObject();
        put(out, "items", items);
        return out;
    }

    private JSONObject pagedConversations(Map<String, String> query) {
        int page = query.containsKey("page") ? intOf(query.get("page"), 0) : -1;
        int pageSize = query.containsKey("pageSize") ? intOf(query.get("pageSize"), 20) : -1;
        int offset = page >= 0 ? page * (pageSize > 0 ? pageSize : 20) : intOf(query.get("offset"), 0);
        int limit = pageSize > 0 ? pageSize : intOf(query.get("limit"), 20);

        JSONArray all = new JSONArray();
        JSONArray listed = conversationList().optJSONArray("items");
        if (listed != null) {
            all = listed;
        }
        JSONArray items = new JSONArray();
        for (int i = offset; i < all.length() && items.length() < limit; i++) {
            items.put(all.opt(i));
        }
        JSONObject out = new JSONObject();
        put(out, "items", items);
        if (page >= 0) {
            put(out, "page", Integer.valueOf(page));
        }
        if (pageSize > 0) {
            put(out, "pageSize", Integer.valueOf(pageSize));
        }
        put(out, "total", Integer.valueOf(all.length()));
        put(out, "hasMore", Boolean.valueOf(offset + items.length() < all.length()));
        if (offset + items.length() < all.length()) {
            put(out, "nextOffset", Integer.valueOf(offset + items.length()));
        } else {
            put(out, "nextOffset", JSONObject.NULL);
        }
        return out;
    }

    private JSONObject summaryOf(JSONObject conversation) {
        JSONObject o = new JSONObject();
        put(o, "id", conversation.optString("id", ""));
        put(o, "assistantId", conversation.optString("assistantId", DEFAULT_ASSISTANT_ID));
        put(o, "title", conversation.optString("title", ""));
        put(o, "isPinned", Boolean.valueOf(conversation.optBoolean("isPinned", false)));
        put(o, "createAt", Long.valueOf(conversation.optLong("createAt", 0)));
        put(o, "updateAt", Long.valueOf(conversation.optLong("updateAt", 0)));
        put(o, "isGenerating", Boolean.valueOf(conversation.optBoolean("isGenerating", false)));
        put(o, "isFork", Boolean.valueOf(conversation.optBoolean("isFork", false)));
        put(o, "isConsolidated", Boolean.valueOf(conversation.optBoolean("isConsolidated", false)));
        put(o, "contextSummaryUpToIndex",
                Integer.valueOf(conversation.optInt("contextSummaryUpToIndex", 0)));
        put(o, "lastPruneTime", Long.valueOf(conversation.optLong("lastPruneTime", 0)));
        put(o, "lastPruneMessageCount",
                Integer.valueOf(conversation.optInt("lastPruneMessageCount", 0)));
        put(o, "lastRefreshTime", Long.valueOf(conversation.optLong("lastRefreshTime", 0)));
        return o;
    }

    // --------------------------------------------------------- conversation SSE

    /**
     * Serves one conversation's event stream.
     *
     * <p>This is the channel generation deltas travel on; the client keeps it open for the
     * lifetime of the conversation view and expects an immediate {@code snapshot} so it can
     * render state that predates this connection.
     */
    private void serveConversationStream(String id, JSONObject conversation, Responder responder) {
        Subscriber sub = new Subscriber(responder, conversationSubscribers);
        conversationSubscribers.add(sub);
        responder.sseComment("connected");

        JSONObject snapshot = new JSONObject();
        put(snapshot, "type", "snapshot");
        put(snapshot, "seq", Long.valueOf(seq.getAndIncrement()));
        put(snapshot, "conversation", conversation);
        put(snapshot, "serverTime", Long.valueOf(System.currentTimeMillis()));
        sub.send("snapshot", snapshot.toString());
    }

    private void broadcastListInvalidate(String assistantId) {
        if (listSubscribers.isEmpty()) {
            return;
        }
        JSONObject o = new JSONObject();
        put(o, "type", "invalidate");
        put(o, "assistantId", assistantId);
        put(o, "timestamp", Long.valueOf(System.currentTimeMillis()));
        String payload = o.toString();
        for (Subscriber s : listSubscribers) {
            s.send("invalidate", payload);
        }
    }

    private void broadcastNode(String conversationId, String nodeId, int nodeIndex,
            JSONObject node, boolean isGenerating, long updateAt) {
        if (conversationSubscribers.isEmpty()) {
            return;
        }
        JSONObject o = new JSONObject();
        put(o, "type", "node_update");
        put(o, "seq", Long.valueOf(seq.getAndIncrement()));
        put(o, "conversationId", conversationId);
        put(o, "nodeId", nodeId);
        put(o, "nodeIndex", Integer.valueOf(nodeIndex));
        put(o, "node", node);
        put(o, "updateAt", Long.valueOf(updateAt));
        put(o, "isGenerating", Boolean.valueOf(isGenerating));
        put(o, "serverTime", Long.valueOf(System.currentTimeMillis()));
        String payload = o.toString();
        for (Subscriber s : conversationSubscribers) {
            s.send("node_update", payload);
        }
    }

    private void broadcastError(String conversationId, String message) {
        JSONObject o = new JSONObject();
        put(o, "type", "error");
        put(o, "message", message);
        String payload = o.toString();
        for (Subscriber s : conversationSubscribers) {
            s.send("error", payload);
        }
    }

    // -------------------------------------------------------------- generation

    private static final class Generation {
        final String conversationId;
        final String nodeId;
        final String modelId;
        final JSONObject conversation;
        final StringBuilder reasoning = new StringBuilder();
        final StringBuilder content = new StringBuilder();
        final boolean wantReasoning;
        volatile boolean finished;

        Generation(String conversationId, String nodeId, String modelId, JSONObject conversation,
                boolean wantReasoning) {
            this.conversationId = conversationId;
            this.nodeId = nodeId;
            this.modelId = modelId;
            this.conversation = conversation;
            this.wantReasoning = wantReasoning;
        }
    }

    /**
     * Starts one assistant turn.
     *
     * <p>The HTTP response is only an acknowledgement — the reply is pushed to the
     * conversation's {@code /stream} subscribers as it arrives.
     */
    private JSONObject startGeneration(String conversationId, JSONObject conversation, String rawBody)
            throws Exception {
        JSONObject in = parseBody(rawBody);
        if (in == null) {
            throw new IOException("message body was not JSON");
        }

        JSONArray nodes = conversation.optJSONArray("messages");
        if (nodes == null) {
            nodes = new JSONArray();
            put(conversation, "messages", nodes);
        }

        JSONArray parts = in.optJSONArray("parts");
        if (parts == null || parts.length() == 0) {
            throw new IOException("message body had no parts");
        }
        String modelId = firstNonEmpty(in.optString("modelId", null),
                in.optString("model", null), currentModelId());
        final boolean wantReasoning = in.optBoolean("reasoning", Boolean.TRUE);

        // Append the user's turn, then an empty assistant node to stream into.
        nodes.put(node(newMessage(messageId(), "USER", parts, null), false));
        String nodeId = messageId();
        String assistantId = messageId();
        final Generation gen = new Generation(conversationId, nodeId, modelId, conversation,
                wantReasoning);
        nodes.put(node(newMessage(assistantId, "ASSISTANT", new JSONArray(), gen.modelId), true));
        final int nodeIndex = nodes.length() - 1;

        put(conversation, "isGenerating", Boolean.TRUE);
        persist(conversationId, conversation);
        broadcastNode(conversationId, nodeId, nodeIndex,
                nodes.optJSONObject(nodeIndex), true, System.currentTimeMillis());

        generations.put(conversationId, gen);

        JSONArray history = new JSONArray();
        for (int i = 0; i <= nodeIndex; i++) {
            JSONObject n = nodes.optJSONObject(i);
            if (n == null) {
                continue;
            }
            JSONArray msgs = n.optJSONArray("messages");
            if (msgs == null) {
                continue;
            }
            for (int m = 0; m < msgs.length(); m++) {
                JSONObject msg = msgs.optJSONObject(m);
                if (msg == null) {
                    continue;
                }
                history.put(relayMessage(msg));
            }
        }

        JSONObject body = new JSONObject();
        put(body, "model", gen.modelId);
        put(body, "messages", history);
        put(body, "stream", Boolean.TRUE);

        int maxTokens = in.optInt("maxTokens", 0);
        if (maxTokens > 0) {
            put(body, "max_tokens", Integer.valueOf(maxTokens));
        }
        double temperature = in.optDouble("temperature", Double.NaN);
        if (!Double.isNaN(temperature)) {
            put(body, "temperature", Double.valueOf(temperature));
        }

        relayClient.streamChat(nodeId, body, new RelayClient.StreamHandler() {
            @Override
            public void onOpen(String requestId) {
                // The client learns of the turn from the node_update above; nothing to add.
            }

            @Override
            public void onDelta(String requestId, String reasoning, String content) {
                if (gen.finished) {
                    return;
                }
                synchronized (gen) {
                    if (reasoning != null) {
                        gen.reasoning.append(reasoning);
                    }
                    if (content != null) {
                        gen.content.append(content);
                    }
                }
                long updateAt = System.currentTimeMillis();
                publishPartial(gen, nodeIndex, updateAt);
            }

            @Override
            public void onDone(String requestId, Integer promptTokens, Integer completionTokens) {
                finishGeneration(gen, nodeIndex, promptTokens, completionTokens);
            }

            @Override
            public void onError(String requestId, String message) {
                gen.finished = true;
                generations.remove(gen.conversationId);
                put(gen.conversation, "isGenerating", Boolean.FALSE);
                persist(gen.conversationId, gen.conversation);
                broadcastError(gen.conversationId, message);
                broadcastListInvalidate(gen.conversation.optString("assistantId", DEFAULT_ASSISTANT_ID));
            }
        });

        JSONObject ack = status("ok");
        put(ack, "ok", Boolean.TRUE);
        put(ack, "generationStatus", "accepted");
        put(ack, "conversationId", conversationId);
        put(ack, "nodeId", nodeId);
        return ack;
    }

    /** Republishes the in-progress assistant node so the UI can render deltas as they land. */
    private void publishPartial(Generation gen, int nodeIndex, long updateAt) {
        JSONArray nodes = gen.conversation.optJSONArray("messages");
        if (nodes == null || nodeIndex >= nodes.length()) {
            return;
        }
        JSONObject node = nodes.optJSONObject(nodeIndex);
        if (node == null) {
            return;
        }
        JSONArray messages = node.optJSONArray("messages");
        if (messages == null || messages.length() == 0) {
            return;
        }
        JSONObject message = messages.optJSONObject(0);
        if (message == null) {
            return;
        }
        JSONArray parts = new JSONArray();
        String reasoning;
        String content;
        synchronized (gen) {
            reasoning = gen.reasoning.toString();
            content = gen.content.toString();
        }
        if (reasoning.length() > 0) {
            parts.put(reasoningPart(reasoning));
        }
        if (content.length() > 0) {
            parts.put(textPart(content));
        }
        put(message, "parts", parts);
        broadcastNode(gen.conversationId, gen.nodeId, nodeIndex, node, true, updateAt);
    }

    private void finishGeneration(Generation gen, int nodeIndex, Integer promptTokens,
            Integer completionTokens) {
        if (gen.finished) {
            return;
        }
        gen.finished = true;
        generations.remove(gen.conversationId);

        JSONArray nodes = gen.conversation.optJSONArray("messages");
        if (nodes != null && nodeIndex < nodes.length()) {
            JSONObject node = nodes.optJSONObject(nodeIndex);
            if (node != null) {
                JSONArray messages = node.optJSONArray("messages");
                JSONObject message = messages == null ? null : messages.optJSONObject(0);
                if (message != null) {
                    JSONArray parts = new JSONArray();
                    String reasoning;
                    String content;
                    synchronized (gen) {
                        reasoning = gen.reasoning.toString();
                        content = gen.content.toString();
                    }
                    if (reasoning.length() > 0) {
                        parts.put(reasoningPart(reasoning));
                    }
                    if (content.length() > 0) {
                        parts.put(textPart(content));
                    }
                    put(message, "parts", parts);
                    put(message, "finishedAt", isoNow());
                    if (promptTokens != null || completionTokens != null) {
                        JSONObject usage = new JSONObject();
                        if (promptTokens != null) {
                            put(usage, "promptTokens", promptTokens);
                            put(usage, "prompt_tokens", promptTokens);
                        }
                        if (completionTokens != null) {
                            put(usage, "completionTokens", completionTokens);
                            put(usage, "completion_tokens", completionTokens);
                        }
                        put(message, "usage", usage);
                    }
                }
            }
        }

        put(gen.conversation, "isGenerating", Boolean.FALSE);
        persist(gen.conversationId, gen.conversation);
        if (nodes != null && nodeIndex < nodes.length()) {
            broadcastNode(gen.conversationId, gen.nodeId, nodeIndex, nodes.optJSONObject(nodeIndex),
                    false, System.currentTimeMillis());
        }
        broadcastListInvalidate(gen.conversation.optString("assistantId", DEFAULT_ASSISTANT_ID));
    }

    private void stopGeneration(String conversationId) {
        Generation gen = generations.remove(conversationId);
        if (gen == null) {
            return;
        }
        relayClient.cancel(gen.nodeId);
        finishGeneration(gen, indexOfNode(gen), null, null);
    }

    private int indexOfNode(Generation gen) {
        JSONArray nodes = gen.conversation.optJSONArray("messages");
        if (nodes == null) {
            return 0;
        }
        for (int i = nodes.length() - 1; i >= 0; i--) {
            JSONObject n = nodes.optJSONObject(i);
            if (n != null && gen.nodeId.equals(n.optString("id", null))) {
                return i;
            }
        }
        return 0;
    }

    // ------------------------------------------------------- DTO construction

    private String messageId() {
        return "m" + Long.toHexString(System.currentTimeMillis())
                + Long.toHexString(idSeq.getAndIncrement());
    }

    private static JSONObject node(JSONObject message, boolean isUser) {
        JSONArray messages = new JSONArray();
        messages.put(message);
        JSONObject n = new JSONObject();
        put(n, "id", message.optString("id"));
        put(n, "messages", messages);
        put(n, "selectIndex", Integer.valueOf(0));
        return n;
    }

    private static JSONObject newMessage(String id, String role, JSONArray parts, String modelId) {
        JSONObject m = new JSONObject();
        put(m, "id", id);
        put(m, "role", role);
        put(m, "parts", parts == null ? new JSONArray() : parts);
        put(m, "createdAt", isoNow());
        if (modelId != null) {
            put(m, "modelId", modelId);
        }
        return m;
    }

    private static JSONObject textPart(String text) {
        JSONObject p = new JSONObject();
        put(p, "type", "text");
        put(p, "text", text);
        return p;
    }

    private static JSONObject reasoningPart(String reasoning) {
        JSONObject p = new JSONObject();
        put(p, "type", "reasoning");
        put(p, "reasoning", reasoning);
        return p;
    }

    /** Converts a stored UI message into the relay's chat-completions shape. */
    private static JSONObject relayMessage(JSONObject message) {
        JSONObject out = new JSONObject();
        put(out, "role", message.optString("role", "user").toLowerCase(java.util.Locale.US));
        JSONArray parts = message.optJSONArray("parts");
        JSONArray content = new JSONArray();
        StringBuilder text = new StringBuilder();
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) {
                JSONObject p = parts.optJSONObject(i);
                if (p == null) {
                    continue;
                }
                String type = p.optString("type", "");
                if (type.equals("text")) {
                    text.append(p.optString("text", ""));
                } else if (type.equals("image")) {
                    JSONObject image = new JSONObject();
                    put(image, "type", "image_url");
                    JSONObject url = new JSONObject();
                    put(url, "url", p.optString("url", ""));
                    put(image, "image_url", url);
                    content.put(image);
                }
            }
        }
        if (text.length() > 0 || content.length() == 0) {
            JSONObject t = new JSONObject();
            put(t, "type", "text");
            put(t, "text", text.toString());
            content.put(t);
        }
        put(out, "content", content);
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private void seedSettings() {
        settings.put("dynamicColor", Boolean.TRUE);
        settings.put("developerMode", Boolean.FALSE);
        settings.put("enableWebSearch", Boolean.FALSE);
        settings.put("favoriteModels", new JSONArray());
        settings.put("assistantId", DEFAULT_ASSISTANT_ID);
        settings.put("themeId", "default");
        settings.put("searchServiceSelected", Integer.valueOf(0));
        settings.put("assistants", defaultAssistants());

        JSONObject stored = store.read("settings.json");
        if (stored != null) {
            for (Iterator<String> it = stored.keys(); it.hasNext(); ) {
                String k = it.next();
                Object val = stored.opt(k);
                if (val != null) {
                    settings.put(k, val);
                }
            }
        }
        Object storedAssistants = settings.get("assistants");
        if (!(storedAssistants instanceof JSONArray) || ((JSONArray) storedAssistants).length() == 0) {
            settings.put("assistants", defaultAssistants());
        }
    }

    private synchronized void persistSettings() {
        JSONObject toSave = new JSONObject();
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            put(toSave, entry.getKey(), entry.getValue());
        }
        store.write("settings.json", toSave);
    }

    /**
     * The single assistant the app ships with.
     *
     * <p>The client resolves its active assistant from this list and disables every control that
     * needs one, so an empty list leaves the app unusable with no way to recover: the model
     * picker never opens, so no model can ever be chosen.
     */
    private static JSONArray defaultAssistants() {
        JSONObject assistant = new JSONObject();
        put(assistant, "id", DEFAULT_ASSISTANT_ID);
        put(assistant, "name", "LastLab");
        put(assistant, "tags", new JSONArray());
        put(assistant, "mcpServers", new JSONArray());
        put(assistant, "modeInjectionIds", new JSONArray());
        put(assistant, "lorebookIds", new JSONArray());
        put(assistant, "useAssistantAvatar", Boolean.TRUE);
        put(assistant, "chatModelId", JSONObject.NULL);
        JSONArray assistants = new JSONArray();
        assistants.put(assistant);
        return assistants;
    }

    /**
     * The model a new conversation uses when the client does not name one.
     *
     * <p>Falls back to the first model the relay actually published, so a request is never sent
     * under an id the relay would reject.
     */
    private String currentModelId() {
        Object stored = settings.get("chatModelId");
        if (stored instanceof String && !((String) stored).isEmpty()) {
            return (String) stored;
        }
        String assistantModel = storedAssistantModelId();
        if (assistantModel != null) {
            return assistantModel;
        }
        List<String> ids = Models.idsOf(lastCatalog());
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return "";
    }

    /** The model chosen on the default assistant, or null when it has none. */
    private String storedAssistantModelId() {
        Object list = settings.get("assistants");
        if (!(list instanceof JSONArray)) {
            return null;
        }
        JSONArray assistants = (JSONArray) list;
        for (int i = 0; i < assistants.length(); i++) {
            JSONObject assistant = assistants.optJSONObject(i);
            if (assistant == null) {
                continue;
            }
            String modelId = assistant.optString("chatModelId", "");
            if (!modelId.isEmpty() && !"null".equals(modelId)) {
                return modelId;
            }
        }
        return null;
    }

    /** The most recent catalog the relay returned, refreshing it when possible. */
    private JSONObject lastCatalog() {
        JSONObject catalog = fetchCatalog();
        if (catalog != null) {
            cachedCatalog = catalog;
            catalogFetchedAt = System.currentTimeMillis();
            return catalog;
        }
        return cachedCatalog;
    }

    private static int intOf(String s, int def) {
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String isoNow() {
        // Matches the ISO-8601 shape the client's MessageDto expects.
        return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .format(new java.util.Date());
    }

    private static JSONObject parseBody(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static void copyOnto(JSONObject target, JSONObject source) {
        if (source == null) {
            return;
        }
        for (Iterator<String> it = source.keys(); it.hasNext(); ) {
            String k = it.next();
            put(target, k, source.opt(k));
        }
    }

    static void put(JSONObject o, String key, Object value) {
        try {
            o.put(key, value);
        } catch (Exception ignored) {
            // Values used here are strings, numbers, booleans and JSON containers, none of
            // which can fail to serialise.
        }
    }

    private static String firstNonEmpty(String a, String b, String c) {
        if (a != null && !a.isEmpty()) {
            return a;
        }
        if (b != null && !b.isEmpty()) {
            return b;
        }
        return c == null ? "" : c;
    }

    /** One connected streaming client. */
    private static final class Subscriber {
        final Responder responder;
        private final java.util.List<Subscriber> owner;

        Subscriber(Responder responder, java.util.List<Subscriber> owner) {
            this.responder = responder;
            this.owner = owner;
        }

        void send(String event, String json) {
            if (responder.isDead()) {
                owner.remove(this);
                return;
            }
            try {
                responder.sse(event, json);
            } catch (RuntimeException dropped) {
                owner.remove(this);
            }
        }
    }
}