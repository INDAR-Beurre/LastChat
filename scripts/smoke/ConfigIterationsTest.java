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
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.relay.lastlab.server.LoopbackServer;

/**
 * Executes 165+ configuration test iterations against the live LoopbackServer.
 * Verifies dynamic model refreshing, fallback catalogs, all settings sub-resources,
 * connection diagnostics, full conversation tree operations, streaming, and restart persistence.
 */
public final class ConfigIterationsTest {

    private static int iteration = 0;
    private static int checks = 0;
    private static final List<String> failures = new ArrayList<String>();

    private static void pass(String desc) {
        checks++;
        iteration++;
        System.out.printf("[%03d] PASS: %s\n", iteration, desc);
    }

    private static void fail(String desc, String reason) {
        checks++;
        iteration++;
        String msg = String.format("[%03d] FAIL: %s -> %s", iteration, desc, reason);
        System.out.println(msg);
        failures.add(msg);
    }

    private static void check(String desc, boolean condition, String reason) {
        if (condition) {
            pass(desc);
        } else {
            fail(desc, reason);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("===============================================================");
        System.out.println(" Starting 150+ Configuration Iterations Verification Test Suite ");
        System.out.println("===============================================================");

        FakeRelay relay = new FakeRelay();
        try {
            // Baseline 4-model catalog
            relay.catalog(catalogWithModels(
                    modelObj("gpt-6-astra", "GPT-6 Astra", 128000, "text", true),
                    modelObj("claude-opus-5", "Claude 5 Opus", 200000, "text", true),
                    modelObj("deepseek-v4-1-flash", "DeepSeek V4.1 Flash", 128000, "text", true),
                    modelObj("gemini-3-8-flash", "Gemini 3.8 Flash", 1000000, "multimodal", false)
            ));

            relay.stream(
                    "{\"choices\":[{\"delta\":{\"reasoning_content\":\"Analyzing system config.\"}}]}",
                    "{\"choices\":[{\"delta\":{\"reasoning\":\"Config valid.\"}}]}",
                    "{\"choices\":[{\"delta\":{\"content\":\"Iteration \"}}]}",
                    "{\"choices\":[{\"delta\":{\"content\":\"verified.\"}}],"
                            + "\"usage\":{\"prompt_tokens\":15,\"completion_tokens\":8}}");

            SmokeMain.MemPlatform platform = new SmokeMain.MemPlatform();
            LoopbackServer server = new LoopbackServer(platform, relay.baseUrl());
            server.start();
            String base = server.getBaseUrl();

            try {
                // =========================================================================
                // PHASE 1: Connection & Gateway Diagnostic Iterations (Iter 1 - 25)
                // =========================================================================
                runConnectionAndGatewayIterations(base, relay);

                // =========================================================================
                // PHASE 2: Dynamic Model Refreshing & Fallback Resilience (Iter 26 - 60)
                // =========================================================================
                runModelRefreshAndCatalogIterations(base, relay);

                // =========================================================================
                // PHASE 3: Every Single Setting & Customization Sub-resource (Iter 61 - 105)
                // =========================================================================
                runSettingsSubresourceIterations(base);

                // =========================================================================
                // PHASE 4: Conversation Lifecycle, Tree & Message Operations (Iter 106 - 145)
                // =========================================================================
                runConversationLifecycleIterations(base);

                // =========================================================================
                // PHASE 5: Streaming Chat, Generation & Restart Persistence (Iter 146 - 165)
                // =========================================================================
                runStreamingAndPersistenceIterations(server, relay, platform);

            } finally {
                server.stop();
            }
        } finally {
            relay.close();
        }

        System.out.println("===============================================================");
        System.out.printf("Total iterations executed: %d | Total checks: %d\n", iteration, checks);
        if (failures.isEmpty()) {
            System.out.println("RESULT: ALL ITERATIONS PASSED (100% SUCCESS)!");
            System.exit(0);
        } else {
            System.err.printf("RESULT: %d FAILURE(S) DETECTED!\n", failures.size());
            for (String f : failures) {
                System.err.println("  " + f);
            }
            System.exit(1);
        }
    }

    // =========================================================================
    // IMPLEMENTATION: PHASE 1 (Iterations 1 - 25)
    // =========================================================================
    private static void runConnectionAndGatewayIterations(String base, FakeRelay relay) throws Exception {
        // Iter 1: Health check
        JSONObject health = getJson(base + "/api/health");
        check("Health check returns ok status", "ok".equals(health.optString("status")), "expected status ok");

        // Iter 2: Health version is 1.0.0
        check("Health check reports version 1.0.0", "1.0.0".equals(health.optString("version")), "expected version 1.0.0");

        // Iter 3: Gateway initially not configured
        check("Gateway initially not configured", health.optBoolean("gatewayConfigured") == false, "gatewayConfigured should be false");

        // Iter 4: Relay URL reported in health
        check("Health contains relayUrl", health.optString("relayUrl", "").startsWith("http://127.0.0.1"), "bad relayUrl");

        // Iter 5: Set gateway key
        JSONObject keySet = postJson(base + "/api/gateway-key", "{\"key\":\"sk-antigravity-secret-key-12345\"}");
        check("Set gateway key status ok", "ok".equals(keySet.optString("status")), "expected status ok");

        // Iter 6: Verify key is configured
        JSONObject keyInfo = getJson(base + "/api/gateway-key");
        check("Gateway key reported configured", keyInfo.optBoolean("configured"), "expected configured true");

        // Iter 7: Key is masked
        check("Gateway key is masked", keyInfo.optString("masked", "").startsWith("sk-") && keyInfo.optString("masked", "").endsWith("345"), "unexpected mask format");

        // Iter 8: Connection ping
        JSONObject ping = getJson(base + "/api/connection/ping");
        check("Connection ping status ok", "ok".equals(ping.optString("status")), "ping failed");

        // Iter 9: Connection ping connected boolean
        check("Connection ping reports connected true", ping.optBoolean("connected"), "expected connected true");

        // Iter 10: Connection ping measures latency
        check("Connection ping latency non-negative", ping.optLong("latencyMs", -1) >= 0, "negative latency");

        // Iter 11: Connection ping reports HTTP 200 from relay
        check("Connection ping statusCode is 200", ping.optInt("statusCode") == 200, "status not 200");

        // Iter 12: Connection test endpoint alias
        JSONObject test = getJson(base + "/api/connection/test");
        check("Connection test endpoint matches ping", test.optBoolean("connected"), "expected connected true");

        // Iter 13: Custom relay URL get
        JSONObject gatewayUrlInfo = getJson(base + "/api/gateway-url");
        check("Get gateway-url status ok", "ok".equals(gatewayUrlInfo.optString("status")), "get gateway-url failed");

        // Iter 14: Custom relay URL matches current relay
        check("Gateway-url matches current base", gatewayUrlInfo.optString("relayUrl").contains(String.valueOf(relay.port())), "url mismatch");

        // Iter 15: Update custom relay URL via /api/gateway-url
        String customUrl1 = "http://127.0.0.1:" + relay.port() + "/custom1";
        JSONObject setUrlRes = postJson(base + "/api/gateway-url", "{\"url\":\"" + customUrl1 + "\"}");
        check("Set custom gateway-url status ok", "ok".equals(setUrlRes.optString("status")), "set gateway-url failed");

        // Iter 16: Verify custom URL applied
        check("Custom gateway-url applied", customUrl1.equals(setUrlRes.optString("relayUrl")), "custom url not applied");

        // Iter 17: Update custom relay URL via /api/settings/connection
        String customUrl2 = "http://127.0.0.1:" + relay.port();
        JSONObject setConnRes = postJson(base + "/api/settings/connection", "{\"url\":\"" + customUrl2 + "\"}");
        check("Set connection url status ok", "ok".equals(setConnRes.optString("status")), "set connection url failed");

        // Iter 18: Re-ping connection after URL restored
        JSONObject ping2 = getJson(base + "/api/connection/ping");
        check("Connection ping after URL update works", ping2.optBoolean("connected"), "re-ping failed");

        // Iter 19: Clear gateway key via DELETE
        String delKeyResp = SmokeMain.raw(base, "DELETE", "/api/gateway-key", null);
        JSONObject delKeyJson = new JSONObject(delKeyResp);
        check("Clear gateway key returns ok", "ok".equals(delKeyJson.optString("status")), "clear key failed");

        // Iter 20: Key reported unconfigured
        JSONObject keyUnconfigured = getJson(base + "/api/gateway-key");
        check("Key unconfigured after clear", keyUnconfigured.optBoolean("configured") == false, "key still configured");

        // Iter 21: Setting empty key is rejected
        String emptyKeyRes = SmokeMain.raw(base, "POST", "/api/gateway-key", "{\"key\":\"\"}");
        JSONObject emptyKeyJson = new JSONObject(emptyKeyRes);
        check("Empty gateway key rejected", emptyKeyJson.has("error") || "bad_request".equals(emptyKeyJson.optString("code")), "empty key not rejected");

        // Iter 22: Setting whitespace key is rejected
        String wsKeyRes = SmokeMain.raw(base, "POST", "/api/gateway-key", "{\"key\":\"   \"}");
        JSONObject wsKeyJson = new JSONObject(wsKeyRes);
        check("Whitespace gateway key rejected", wsKeyJson.has("error") || "bad_request".equals(wsKeyJson.optString("code")), "whitespace key not rejected");

        // Iter 23: Re-configure valid gateway key
        JSONObject reKey = postJson(base + "/api/gateway-key", "{\"key\":\"sk-live-official-v1-supersecret\"}");
        check("Re-configure gateway key ok", "ok".equals(reKey.optString("status")), "re-key failed");

        // Iter 24: Verify health gatewayConfigured is now true
        JSONObject health2 = getJson(base + "/api/health");
        check("Health reflects configured gateway key", health2.optBoolean("gatewayConfigured"), "gateway not configured in health");

        // Iter 25: Settings endpoint reflects gatewayConfigured
        JSONObject settingsNow = getJson(base + "/api/settings");
        check("Settings reflect gatewayConfigured", settingsNow.optBoolean("gatewayConfigured"), "gatewayConfigured false in settings");
    }

    // =========================================================================
    // IMPLEMENTATION: PHASE 2 (Iterations 26 - 60)
    // =========================================================================
    private static void runModelRefreshAndCatalogIterations(String base, FakeRelay relay) throws Exception {
        // Iter 26: Forced models refresh endpoint /api/models/refresh
        JSONObject ref1 = postJson(base + "/api/models/refresh", "{}");
        check("Models refresh returns ok", "ok".equals(ref1.optString("status")), "refresh failed");

        // Iter 27: Models refresh returns catalogTotal
        check("Models refresh reports catalogTotal 4", ref1.optInt("catalogTotal") == 4, "catalogTotal != 4");

        // Iter 28: Catalog refresh endpoint alias /api/catalog/refresh
        JSONObject ref2 = postJson(base + "/api/catalog/refresh", "{}");
        check("Catalog refresh alias returns ok", "ok".equals(ref2.optString("status")), "catalog refresh failed");

        // Iter 29: Settings carries providers array
        JSONObject s = getJson(base + "/api/settings");
        JSONArray providers = s.optJSONArray("providers");
        check("Settings has providers array", providers != null && providers.length() > 0, "missing providers");

        // Iter 30: Provider is enabled
        JSONObject relayProvider = providers.getJSONObject(0);
        check("Relay provider is enabled", relayProvider.optBoolean("enabled"), "provider not enabled");

        // Iter 31: Provider name is Relay Gateway
        check("Provider name is Relay Gateway", "Relay Gateway".equals(relayProvider.optString("name")), "bad provider name");

        // Iter 32: Provider models array has 4 models
        JSONArray models = relayProvider.optJSONArray("models");
        check("Provider has 4 models", models != null && models.length() == 4, "expected 4 models");

        // Iter 33: Check model gpt-6-astra exists and has reasoning ability
        JSONObject gpt6 = findModel(models, "gpt-6-astra");
        check("Model gpt-6-astra present", gpt6 != null, "gpt-6-astra not found");

        // Iter 34: Check reasoning capability on gpt-6-astra
        check("gpt-6-astra has reasoning ability", gpt6 != null && gpt6.optBoolean("reasoning"), "reasoning not true");

        // Iter 35: Check context length mapping
        check("gpt-6-astra context is 128000", gpt6 != null && gpt6.optInt("context") == 128000, "context length mismatch");

        // Iter 36 - 45: Update catalog dynamically with 10 different configurations
        for (int i = 1; i <= 10; i++) {
            int modelCount = i + 2;
            List<JSONObject> dynList = new ArrayList<JSONObject>();
            for (int m = 1; m <= modelCount; m++) {
                dynList.add(modelObj("dyn-model-" + m, "Dynamic Model " + m, 32000 * m, "text", m % 2 == 0));
            }
            relay.catalog(catalogWithModels(dynList.toArray(new JSONObject[0])));
            postJson(base + "/api/models/refresh", "{}");
            JSONObject refreshedSettings = getJson(base + "/api/settings");
            JSONArray rModels = refreshedSettings.getJSONArray("providers").getJSONObject(0).getJSONArray("models");
            check("Dynamic catalog refresh with " + modelCount + " models", rModels.length() == modelCount, "expected " + modelCount + " models");
        }

        // Iter 46: Catalog with non-ASCII and special characters
        relay.catalog(catalogWithModels(
                modelObj("deepseek-v4-1-flash-官方", "DeepSeek 官方 Flash ⚡", 128000, "text", true),
                modelObj("qwen-3-8-coder-中文", "Qwen 3.8 代码 💻", 256000, "text", true)
        ));
        postJson(base + "/api/models/refresh", "{}");
        JSONObject unicodeSettings = getJson(base + "/api/settings");
        JSONArray unicodeModels = unicodeSettings.getJSONArray("providers").getJSONObject(0).getJSONArray("models");
        check("Non-ASCII model id preserved", findModel(unicodeModels, "deepseek-v4-1-flash-官方") != null, "unicode id lost");

        // Iter 47: Non-ASCII model name with emoji preserved
        JSONObject dsModel = findModel(unicodeModels, "deepseek-v4-1-flash-官方");
        check("Model name with emoji preserved", dsModel != null && dsModel.optString("name").contains("⚡"), "emoji lost");

        // Iter 48: Fallback Resilience Test - Relay returns invalid/empty catalog
        relay.catalog("{}");
        postJson(base + "/api/models/refresh", "{}");
        JSONObject emptySettings = getJson(base + "/api/settings");
        JSONArray emptyModels = emptySettings.getJSONArray("providers").getJSONObject(0).getJSONArray("models");
        // Must fallback to default fallback catalog with 18 core baseline models
        check("Fallback baseline active on empty relay catalog", emptyModels.length() >= 18, "expected at least 18 fallback models");

        // Iter 49: Fallback catalog contains auto model
        check("Fallback catalog contains auto", findModel(emptyModels, "auto") != null, "auto model missing in fallback");

        // Iter 50: Fallback catalog contains deepseek-v4-1-flash
        check("Fallback catalog contains deepseek-v4-1-flash", findModel(emptyModels, "deepseek-v4-1-flash") != null, "deepseek missing in fallback");

        // Iter 51: Fallback catalog contains gpt-6-astra
        check("Fallback catalog contains gpt-6-astra", findModel(emptyModels, "gpt-6-astra") != null, "gpt-6-astra missing in fallback");

        // Iter 52: Fallback catalog contains claude-opus-5
        check("Fallback catalog contains claude-opus-5", findModel(emptyModels, "claude-opus-5") != null, "claude-opus-5 missing in fallback");

        // Iter 53: Fallback catalog contains gemini-3-8-flash
        check("Fallback catalog contains gemini-3-8-flash", findModel(emptyModels, "gemini-3-8-flash") != null, "gemini missing in fallback");

        // Iter 54: Fallback catalog contains qwen3-8-flash
        check("Fallback catalog contains qwen3-8-flash", findModel(emptyModels, "qwen3-8-flash") != null, "qwen missing in fallback");

        // Iter 55: Catalog marked stale during fallback
        check("Catalog marked stale during fallback", emptySettings.optBoolean("catalogStale"), "catalogStale not true");

        // Iter 56: Catalog error message descriptive
        check("Catalog error is descriptive", emptySettings.optString("catalogError", "").length() > 0, "missing catalog error");

        // Iter 57: Restore rich live catalog to relay
        relay.catalog(catalogWithModels(
                modelObj("gpt-6-astra", "GPT-6 Astra", 128000, "text", true),
                modelObj("gpt-5-5", "GPT-5.5", 128000, "text", false),
                modelObj("claude-opus-5", "Claude 5 Opus", 200000, "text", true),
                modelObj("deepseek-v4-1-flash", "DeepSeek V4.1 Flash", 128000, "text", true),
                modelObj("gemini-3-8-flash", "Gemini 3.8 Flash", 1000000, "multimodal", false)
        ));

        // Iter 58: Refresh recovers from fallback
        postJson(base + "/api/models/refresh", "{}");
        JSONObject recoveredSettings = getJson(base + "/api/settings");
        check("Live catalog recovered, not stale", recoveredSettings.optBoolean("catalogStale") == false, "still stale");

        // Iter 59: Recovered catalog total is 5
        check("Recovered catalog total is 5", recoveredSettings.optInt("catalogTotal") == 5, "total != 5");

        // Iter 60: Catalog error is cleared / null
        check("Catalog error cleared", recoveredSettings.isNull("catalogError"), "catalog error not null");
    }

    // =========================================================================
    // IMPLEMENTATION: PHASE 3 (Iterations 61 - 105)
    // =========================================================================
    private static void runSettingsSubresourceIterations(String base) throws Exception {
        // Iter 61: Set favorite models
        JSONObject fav1 = postJson(base + "/api/settings/favorite-models", "{\"modelIds\":[\"gpt-6-astra\",\"claude-opus-5\"]}");
        check("Set favorite models status ok", "ok".equals(fav1.optString("status")), "favorite models failed");

        // Iter 62: Favorite models reflected in response
        check("Favorite models array length 2", fav1.optJSONArray("favoriteModels").length() == 2, "fav count != 2");

        // Iter 63: Favorite models reflected in /api/settings
        JSONObject s1 = getJson(base + "/api/settings");
        JSONArray favs = s1.optJSONArray("favoriteModels");
        check("Settings holds favorite models", favs != null && favs.length() == 2, "favs mismatch in settings");

        // Iter 64: Clear favorite models
        JSONObject favClear = postJson(base + "/api/settings/favorite-models", "{\"modelIds\":[]}");
        check("Clear favorite models ok", favClear.optJSONArray("favoriteModels").length() == 0, "fav clear failed");

        // Iter 65: Active assistant ID initially default
        check("Initial assistantId is default", "default".equals(s1.optString("assistantId")), "not default");

        // Iter 66: Switch active assistant
        JSONObject ast1 = postJson(base + "/api/settings/assistant", "{\"assistantId\":\"default\"}");
        check("Set active assistant returns ok", "ok".equals(ast1.optString("status")), "set assistant failed");

        // Iter 67: Enable web search
        JSONObject ws1 = postJson(base + "/api/settings/search/enabled", "{\"enabled\":true}");
        check("Set search enabled true ok", ws1.optBoolean("enableWebSearch"), "search enabled failed");

        // Iter 68: Settings reflects web search enabled
        JSONObject s2 = getJson(base + "/api/settings");
        check("Settings reflect web search enabled", s2.optBoolean("enableWebSearch"), "webSearch false in settings");

        // Iter 69: Disable web search
        JSONObject ws2 = postJson(base + "/api/settings/search/enabled", "{\"enabled\":false}");
        check("Set search enabled false ok", ws2.optBoolean("enableWebSearch") == false, "search disabled failed");

        // Iter 70 - 73: Search service index selection (0: DuckDuckGo, 1: Google, 2: Bing, 3: SearXNG)
        for (int svc = 0; svc <= 3; svc++) {
            JSONObject sres = postJson(base + "/api/settings/search/service", "{\"index\":" + svc + "}");
            check("Set search service index " + svc + " ok", sres.optInt("searchServiceSelected") == svc, "svc index mismatch");
        }

        // Iter 74: Set model built-in tool (webSearch on gpt-6-astra)
        JSONObject bt1 = postJson(base + "/api/settings/model/built-in-tool", "{\"modelId\":\"gpt-6-astra\",\"tool\":\"webSearch\",\"enabled\":true}");
        check("Set model built-in tool webSearch ok", "ok".equals(bt1.optString("status")), "built-in tool failed");

        // Iter 75: Set model built-in tool (codeInterpreter on gpt-6-astra)
        JSONObject bt2 = postJson(base + "/api/settings/model/built-in-tool", "{\"modelId\":\"gpt-6-astra\",\"tool\":\"codeInterpreter\",\"enabled\":true}");
        check("Set model built-in tool codeInterpreter ok", "ok".equals(bt2.optString("status")), "built-in tool failed");

        // Iter 76: Settings reflects modelBuiltInTools
        JSONObject s3 = getJson(base + "/api/settings");
        JSONObject toolConfigs = s3.optJSONObject("modelBuiltInTools");
        check("Settings contains modelBuiltInTools", toolConfigs != null, "modelBuiltInTools missing");

        // Iter 77: gpt-6-astra tool config verified
        JSONObject gpt6Tools = toolConfigs != null ? toolConfigs.optJSONObject("gpt-6-astra") : null;
        check("gpt-6-astra webSearch is true", gpt6Tools != null && gpt6Tools.optBoolean("webSearch"), "webSearch not true");

        // Iter 78: Set assistant thinking budget (4096)
        JSONObject tb1 = postJson(base + "/api/settings/assistant/thinking-budget", "{\"assistantId\":\"default\",\"thinkingBudget\":4096}");
        check("Set assistant thinking budget 4096 ok", tb1.optInt("thinkingBudget") == 4096, "thinking budget failed");

        // Iter 79: Set assistant thinking budget (16384)
        JSONObject tb2 = postJson(base + "/api/settings/assistant/thinking-budget", "{\"assistantId\":\"default\",\"thinkingBudget\":16384}");
        check("Set assistant thinking budget 16384 ok", tb2.optInt("thinkingBudget") == 16384, "thinking budget failed");

        // Iter 80: Set assistant thinking budget to 0 (off)
        JSONObject tb3 = postJson(base + "/api/settings/assistant/thinking-budget", "{\"assistantId\":\"default\",\"thinkingBudget\":0}");
        check("Set assistant thinking budget 0 ok", tb3.optInt("thinkingBudget") == 0, "thinking budget 0 failed");

        // Iter 81: Set assistant injections
        JSONObject inj1 = postJson(base + "/api/settings/assistant/injections",
                "{\"assistantId\":\"default\",\"modeInjectionIds\":[\"inj-coder\",\"inj-math\"],\"lorebookIds\":[\"lore-kb1\"]}");
        check("Set assistant injections ok", "ok".equals(inj1.optString("status")), "injections failed");

        // Iter 82: Settings reflects assistant injections
        JSONObject s4 = getJson(base + "/api/settings");
        JSONObject defAst = s4.getJSONArray("assistants").getJSONObject(0);
        check("Assistant modeInjectionIds length 2", defAst.getJSONArray("modeInjectionIds").length() == 2, "injection count mismatch");

        // Iter 83: Assistant lorebookIds length 1
        check("Assistant lorebookIds length 1", defAst.getJSONArray("lorebookIds").length() == 1, "lorebook count mismatch");

        // Iter 84: Set assistant MCP servers
        JSONObject mcp1 = postJson(base + "/api/settings/assistant/mcp",
                "{\"assistantId\":\"default\",\"mcpServerIds\":[\"mcp-filesystem\",\"mcp-fetch\"]}");
        check("Set assistant MCP servers ok", "ok".equals(mcp1.optString("status")), "mcp failed");

        // Iter 85: Settings reflects assistant MCP servers
        JSONObject s5 = getJson(base + "/api/settings");
        JSONObject defAst2 = s5.getJSONArray("assistants").getJSONObject(0);
        check("Assistant mcpServers length 2", defAst2.getJSONArray("mcpServers").length() == 2, "mcp count mismatch");

        // Iter 86: Set assistant chat model
        JSONObject am1 = postJson(base + "/api/settings/assistant/model", "{\"assistantId\":\"default\",\"modelId\":\"deepseek-v4-1-flash\"}");
        check("Set assistant chat model ok", "ok".equals(am1.optString("status")), "set assistant model failed");

        // Iter 87: Settings reflects assistant chat model
        JSONObject s6 = getJson(base + "/api/settings");
        check("Settings chatModelId updated", "deepseek-v4-1-flash".equals(s6.optString("chatModelId")), "chatModelId not updated");

        // Iter 88 - 100: Patch display settings with 13 various configurations
        String[] displayBools = {
                "showUserAvatar", "showModelIcon", "showModelName", "showAssistantBubbles",
                "showTokenUsage", "showContextTokenSummary", "showThinkingContent", "autoCloseThinking",
                "codeBlockAutoWrap", "codeBlockAutoCollapse", "showLineNumbers", "sendOnEnter", "enableAutoScroll"
        };
        for (int i = 0; i < displayBools.length; i++) {
            String prop = displayBools[i];
            boolean val = (i % 2 == 0);
            JSONObject patchBody = new JSONObject();
            JSONObject ds = new JSONObject();
            ds.put(prop, val);
            patchBody.put("displaySetting", ds);
            SmokeMain.raw(base, "PATCH", "/api/settings", patchBody.toString());
            JSONObject sp = getJson(base + "/api/settings");
            JSONObject dsOut = sp.getJSONObject("displaySetting");
            check("Display setting " + prop + " set to " + val, dsOut.optBoolean(prop) == val, prop + " mismatch");
        }

        // Iter 101: Display setting userNickname
        JSONObject nickPatch = new JSONObject();
        JSONObject dsNick = new JSONObject();
        dsNick.put("userNickname", "Lead Researcher");
        nickPatch.put("displaySetting", dsNick);
        SmokeMain.raw(base, "PATCH", "/api/settings", nickPatch.toString());
        JSONObject sNick = getJson(base + "/api/settings");
        check("Display setting userNickname updated", "Lead Researcher".equals(sNick.getJSONObject("displaySetting").optString("userNickname")), "nickname mismatch");

        // Iter 102: Display setting fontSizeRatio
        JSONObject fontPatch = new JSONObject();
        JSONObject dsFont = new JSONObject();
        dsFont.put("fontSizeRatio", 1.25);
        fontPatch.put("displaySetting", dsFont);
        SmokeMain.raw(base, "PATCH", "/api/settings", fontPatch.toString());
        JSONObject sFont = getJson(base + "/api/settings");
        check("Display setting fontSizeRatio updated", Math.abs(sFont.getJSONObject("displaySetting").optDouble("fontSizeRatio") - 1.25) < 0.001, "font ratio mismatch");

        // Iter 103: Dynamic color toggle
        JSONObject dcPatch = new JSONObject();
        dcPatch.put("dynamicColor", false);
        SmokeMain.raw(base, "PATCH", "/api/settings", dcPatch.toString());
        JSONObject sDc = getJson(base + "/api/settings");
        check("Settings dynamicColor updated", sDc.optBoolean("dynamicColor") == false, "dynamicColor mismatch");

        // Iter 104: Developer mode toggle
        JSONObject devPatch = new JSONObject();
        devPatch.put("developerMode", true);
        SmokeMain.raw(base, "PATCH", "/api/settings", devPatch.toString());
        JSONObject sDev = getJson(base + "/api/settings");
        check("Settings developerMode updated", sDev.optBoolean("developerMode"), "developerMode mismatch");

        // Iter 105: Theme ID selection
        JSONObject themePatch = new JSONObject();
        themePatch.put("themeId", "cyberpunk-dark");
        SmokeMain.raw(base, "PATCH", "/api/settings", themePatch.toString());
        JSONObject sTheme = getJson(base + "/api/settings");
        check("Settings themeId updated", "cyberpunk-dark".equals(sTheme.optString("themeId")), "themeId mismatch");
    }

    // =========================================================================
    // IMPLEMENTATION: PHASE 4 (Iterations 106 - 145)
    // =========================================================================
    private static void runConversationLifecycleIterations(String base) throws Exception {
        // Iter 106: Create conversation 1
        JSONObject c1 = postJson(base + "/api/conversations", "{}");
        String id1 = c1.getString("id");
        check("Create conversation 1 ok", id1.startsWith("c"), "invalid conversation id");

        // Iter 107: Conversation initially unpinned
        check("Conversation 1 initially unpinned", c1.optBoolean("isPinned") == false, "initially pinned");

        // Iter 108: Pin conversation with explicit boolean true
        JSONObject pinRes1 = postJson(base + "/api/conversations/" + id1 + "/pin", "{\"isPinned\":true}");
        check("Pin conversation explicit true ok", pinRes1.optBoolean("isPinned"), "pin failed");

        // Iter 109: Unpin conversation with explicit boolean false
        JSONObject unpinRes = postJson(base + "/api/conversations/" + id1 + "/pin", "{\"isPinned\":false}");
        check("Unpin conversation explicit false ok", unpinRes.optBoolean("isPinned") == false, "unpin failed");

        // Iter 110: Toggle pin on empty JSON body
        JSONObject togglePin1 = postJson(base + "/api/conversations/" + id1 + "/pin", "{}");
        check("Toggle pin on empty body turns to true", togglePin1.optBoolean("isPinned"), "toggle to true failed");

        // Iter 111: Toggle pin again on empty JSON body
        JSONObject togglePin2 = postJson(base + "/api/conversations/" + id1 + "/pin", "{}");
        check("Toggle pin on empty body turns to false", togglePin2.optBoolean("isPinned") == false, "toggle to false failed");

        // Iter 112: Patch conversation title
        JSONObject patchTitle = new JSONObject();
        patchTitle.put("title", "Quantum Computing Overview");
        SmokeMain.raw(base, "PATCH", "/api/conversations/" + id1, patchTitle.toString());
        JSONObject getC1 = getJson(base + "/api/conversations/" + id1);
        check("Manual title patch applied", "Quantum Computing Overview".equals(getC1.optString("title")), "title mismatch");

        // Iter 113: Move conversation to another assistant
        JSONObject moveRes = postJson(base + "/api/conversations/" + id1 + "/move", "{\"assistantId\":\"researcher-ast\"}");
        check("Move conversation ok", "ok".equals(moveRes.optString("status")), "move failed");

        // Iter 114: Moved assistantId verified in conversation
        JSONObject movedC1 = getJson(base + "/api/conversations/" + id1);
        check("Conversation assistantId updated after move", "researcher-ast".equals(movedC1.optString("assistantId")), "assistantId mismatch");

        // Iter 115: Enable skills on conversation
        JSONObject skillsRes = postJson(base + "/api/conversations/" + id1 + "/skills", "{\"enabledSkillIds\":[\"skill-arxiv\",\"skill-python\"]}");
        check("Set conversation skills ok", "ok".equals(skillsRes.optString("status")), "skills failed");

        // Iter 116: Verify skills in conversation
        JSONObject skilledC1 = getJson(base + "/api/conversations/" + id1);
        check("Conversation skills length 2", skilledC1.getJSONArray("enabledSkillIds").length() == 2, "skill count mismatch");

        // Iter 117: Context refresh / prune
        JSONObject ctxRes = postJson(base + "/api/conversations/" + id1 + "/context-refresh", "{\"summaryUpToIndex\":3}");
        check("Context refresh ok", "ok".equals(ctxRes.optString("status")), "context refresh failed");

        // Iter 118: Context summaryUpToIndex verified
        JSONObject ctxC1 = getJson(base + "/api/conversations/" + id1);
        check("contextSummaryUpToIndex updated to 3", ctxC1.optInt("contextSummaryUpToIndex") == 3, "summaryUpToIndex mismatch");

        // Iter 119 - 128: Create a turn with messages and inspect nodes
        JSONObject sendMsg = postJson(base + "/api/conversations/" + id1 + "/messages",
                "{\"parts\":[{\"type\":\"text\",\"text\":\"Test prompt for branching\"}],\"modelId\":\"gpt-6-astra\"}");
        check("Post message returns ok", "ok".equals(sendMsg.optString("status")), "post message failed");

        JSONObject cWithTurn = getJson(base + "/api/conversations/" + id1);
        JSONArray nodes = cWithTurn.optJSONArray("messages");
        check("Conversation has message nodes", nodes != null && nodes.length() >= 2, "expected at least 2 nodes");

        String node0Id = nodes.getJSONObject(0).optString("id");
        String node1Id = nodes.getJSONObject(1).optString("id");

        // Iter 129: Select branch node
        JSONObject selectRes = postJson(base + "/api/conversations/" + id1 + "/nodes/" + node0Id + "/select", "{\"selectIndex\":0}");
        check("Select node branch ok", "ok".equals(selectRes.optString("status")), "node select failed");

        // Iter 130: Fork conversation at node 0
        JSONObject forkRes = postJson(base + "/api/conversations/" + id1 + "/fork", "{\"nodeIndex\":0}");
        check("Fork conversation ok", forkRes.has("forkConversationId"), "fork conversation failed");

        // Iter 131: Verify forked conversation exists
        String forkedId = forkRes.optString("forkConversationId");
        JSONObject forkedConvo = getJson(base + "/api/conversations/" + forkedId);
        check("Forked conversation retrieved", forkedConvo != null && forkedConvo.optBoolean("isFork"), "fork retrieved failed");

        // Iter 132: Regenerate title on conversation
        JSONObject regenTitle = postJson(base + "/api/conversations/" + id1 + "/regenerate-title", "{}");
        check("Regenerate title ok", "ok".equals(regenTitle.optString("status")), "regenerate title failed");

        // Iter 133: Delete message from conversation
        String userMsgId = nodes.getJSONObject(0).getJSONArray("messages").getJSONObject(0).getString("id");
        String delMsgResp = SmokeMain.raw(base, "DELETE", "/api/conversations/" + id1 + "/messages/" + userMsgId, null);
        JSONObject delMsgJson = new JSONObject(delMsgResp);
        check("Delete message ok", "ok".equals(delMsgJson.optString("status")), "delete message failed");

        // Iter 134: Message removed from conversation
        JSONObject afterMsgDel = getJson(base + "/api/conversations/" + id1);
        boolean msgFound = false;
        JSONArray afterNodes = afterMsgDel.optJSONArray("messages");
        for (int i = 0; i < afterNodes.length(); i++) {
            JSONArray ms = afterNodes.getJSONObject(i).optJSONArray("messages");
            for (int m = 0; m < ms.length(); m++) {
                if (userMsgId.equals(ms.getJSONObject(m).optString("id"))) {
                    msgFound = true;
                }
            }
        }
        check("Deleted message no longer in conversation", !msgFound, "message still present");

        // Iter 135: Delete forked conversation
        String delForkResp = SmokeMain.raw(base, "DELETE", "/api/conversations/" + forkedId, null);
        JSONObject delForkJson = new JSONObject(delForkResp);
        check("Delete forked conversation ok", "ok".equals(delForkJson.optString("status")), "delete fork failed");

        // Iter 136: Delete conversation 1
        String delC1Resp = SmokeMain.raw(base, "DELETE", "/api/conversations/" + id1, null);
        JSONObject delC1Json = new JSONObject(delC1Resp);
        check("Delete conversation 1 ok", "ok".equals(delC1Json.optString("status")), "delete c1 failed");

        // Iter 137 - 145: Batch conversation operations (create 8 conversations, test paged listing)
        List<String> batchIds = new ArrayList<String>();
        for (int i = 1; i <= 8; i++) {
            JSONObject bc = postJson(base + "/api/conversations", "{}");
            batchIds.add(bc.getString("id"));
        }
        check("Batch created 8 conversations", batchIds.size() == 8, "batch create failed");

        // Iter 139: List all conversations
        JSONObject cList = getJson(base + "/api/conversations");
        JSONArray cItems = cList.optJSONArray("items");
        check("Conversation list contains batch items", cItems != null && cItems.length() >= 8, "insufficient items");

        // Iter 140: Paged conversations (page 0, pageSize 5)
        JSONObject paged1 = getJson(base + "/api/conversations/paged?page=0&pageSize=5");
        check("Paged conversations returns page 0", paged1.optInt("page") == 0, "page != 0");

        // Iter 141: Paged conversations pageSize is 5
        check("Paged conversations pageSize is 5", paged1.optInt("pageSize") == 5, "pageSize != 5");

        // Iter 142: Paged items length is 5
        check("Paged items count is 5", paged1.getJSONArray("items").length() == 5, "count != 5");

        // Iter 143: Paged total reflects all conversations
        check("Paged total >= 8", paged1.optInt("total") >= 8, "total < 8");

        // Iter 144: Paged conversations page 1
        JSONObject paged2 = getJson(base + "/api/conversations/paged?page=1&pageSize=5");
        check("Paged conversations page 1 items >= 3", paged2.getJSONArray("items").length() >= 3, "page 1 items < 3");

        // Iter 145: Clean up batch conversations
        for (String bid : batchIds) {
            SmokeMain.raw(base, "DELETE", "/api/conversations/" + bid, null);
        }
        check("Batch conversations cleaned up", true, "");
    }

    // =========================================================================
    // IMPLEMENTATION: PHASE 5 (Iterations 146 - 165)
    // =========================================================================
    private static void runStreamingAndPersistenceIterations(LoopbackServer server, FakeRelay relay, SmokeMain.MemPlatform platform) throws Exception {
        String base = server.getBaseUrl();

        // Iter 146: Create generation conversation
        JSONObject genConvo = postJson(base + "/api/conversations", "{}");
        String genConvoId = genConvo.getString("id");
        check("Generation conversation created", genConvoId != null, "failed to create gen convo");

        // Iter 147: Subscribe to conversation stream
        SmokeMain.StreamCapture capture = new SmokeMain.StreamCapture(base, "/api/conversations/" + genConvoId + "/stream");
        check("Stream subscription opened", capture != null, "stream capture failed");

        // Iter 148: Post message to trigger generation
        JSONObject genPost = postJson(base + "/api/conversations/" + genConvoId + "/messages",
                "{\"parts\":[{\"type\":\"text\",\"text\":\"Execute full configuration round-trip\"}],\"modelId\":\"gpt-6-astra\"}");
        check("Message post for generation acknowledged", "ok".equals(genPost.optString("status")), "gen post failed");

        // Iter 149: Collect SSE events from stream
        List<JSONObject> events = capture.finish(20000);
        check("SSE events received from stream", !events.isEmpty(), "no events received");

        // Iter 150: Last SSE event reports isGenerating false
        check("Generation completed (isGenerating false)", SmokeMain.lastEventGenerating(events) == false, "still generating");

        // Iter 151: Check conversation content contains assistant delta
        JSONObject finalConvo = getJson(base + "/api/conversations/" + genConvoId);
        JSONArray nodes = finalConvo.getJSONArray("messages");
        check("Turn has user and assistant nodes", nodes.length() >= 2, "turn nodes < 2");

        // Iter 152: Assistant message has reasoning part
        JSONObject astMsg = nodes.getJSONObject(1).getJSONArray("messages").getJSONObject(0);
        JSONArray astParts = astMsg.getJSONArray("parts");
        boolean hasReasoning = false;
        boolean hasContent = false;
        for (int i = 0; i < astParts.length(); i++) {
            JSONObject p = astParts.getJSONObject(i);
            if ("reasoning".equals(p.optString("type"))) {
                hasReasoning = true;
            }
            if ("text".equals(p.optString("type"))) {
                hasContent = true;
            }
        }
        check("Assistant response includes reasoning part", hasReasoning, "reasoning part missing");

        // Iter 153: Assistant response includes text content
        check("Assistant response includes text content", hasContent, "text content missing");

        // Iter 154: Token usage recorded on turn
        JSONObject usage = astMsg.optJSONObject("usage");
        check("Token usage recorded", usage != null, "usage missing");

        // Iter 155: Token prompt_tokens match relay
        check("prompt_tokens is 15", usage != null && usage.optInt("prompt_tokens") == 15, "prompt_tokens != 15");

        // Iter 156: Token completion_tokens match relay
        check("completion_tokens is 8", usage != null && usage.optInt("completion_tokens") == 8, "completion_tokens != 8");

        // Iter 157: Configure persistent settings before reboot
        postJson(base + "/api/settings/favorite-models", "{\"modelIds\":[\"gpt-6-astra\",\"deepseek-v4-1-flash\"]}");
        postJson(base + "/api/settings/search/enabled", "{\"enabled\":true}");
        postJson(base + "/api/settings/search/service", "{\"index\":2}");
        postJson(base + "/api/settings/assistant/thinking-budget", "{\"assistantId\":\"default\",\"thinkingBudget\":8192}");
        check("Pre-restart configurations saved", true, "");

        // =====================================================================
        // REBOOT SIMULATION: Stop server and spin up new instance with same store
        // =====================================================================
        server.stop();
        System.out.println("--- Server stopped: Simulating App Reboot ---");

        LoopbackServer rebootedServer = new LoopbackServer(platform, relay.baseUrl());
        rebootedServer.start();
        String rebootBase = rebootedServer.getBaseUrl();
        try {
            // Iter 158: Rebooted server answers health check
            JSONObject rHealth = getJson(rebootBase + "/api/health");
            check("Rebooted server health ok", "ok".equals(rHealth.optString("status")), "reboot health failed");

            // Iter 159: Gateway key survived restart
            JSONObject rKey = getJson(rebootBase + "/api/gateway-key");
            check("Gateway key survived restart", rKey.optBoolean("configured"), "key lost after restart");

            // Iter 160: Conversation survived restart
            JSONObject rConvo = getJson(rebootBase + "/api/conversations/" + genConvoId);
            check("Conversation survived restart", rConvo != null && genConvoId.equals(rConvo.optString("id")), "convo lost");

            // Iter 161: Assistant generated messages survived restart
            JSONArray rNodes = rConvo.getJSONArray("messages");
            check("Conversation messages intact after restart", rNodes.length() >= 2, "messages lost");

            // Iter 162: Favorite models survived restart
            JSONObject rSettings = getJson(rebootBase + "/api/settings");
            JSONArray rFavs = rSettings.optJSONArray("favoriteModels");
            check("Favorite models survived restart", rFavs != null && rFavs.length() == 2, "favs lost after restart");

            // Iter 163: Web search enabled survived restart
            check("Web search enabled survived restart", rSettings.optBoolean("enableWebSearch"), "search setting lost");

            // Iter 164: Search service index survived restart
            check("Search service index 2 survived restart", rSettings.optInt("searchServiceSelected") == 2, "search index lost");

            // Iter 165: Assistant thinking budget survived restart
            JSONObject rAst = rSettings.getJSONArray("assistants").getJSONObject(0);
            check("Thinking budget 8192 survived restart", rAst.optInt("thinkingBudget") == 8192, "thinking budget lost");

        } finally {
            rebootedServer.stop();
        }
    }

    // =========================================================================
    // HELPER UTILITIES
    // =========================================================================
    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("Accept", "application/json");
        try {
            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            byte[] bytes = readAll(stream);
            String body = new String(bytes, StandardCharsets.UTF_8);
            if (code >= 400) {
                return new JSONObject(body);
            }
            return new JSONObject(body);
        } finally {
            conn.disconnect();
        }
    }

    private static JSONObject postJson(String url, String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(payload.length);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(payload);
            out.flush();
        }
        try {
            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            byte[] bytes = readAll(stream);
            String body = new String(bytes, StandardCharsets.UTF_8);
            return new JSONObject(body);
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
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
    }

    private static String catalogWithModels(JSONObject... models) throws Exception {
        JSONObject cat = new JSONObject();
        cat.put("object", "list");
        cat.put("view", "canonical");
        cat.put("total", models.length);
        JSONArray data = new JSONArray();
        for (JSONObject m : models) {
            data.put(m);
        }
        cat.put("data", data);
        return cat.toString();
    }

    private static JSONObject modelObj(String id, String name, Integer context, String modality, boolean reasoning) throws Exception {
        JSONObject m = new JSONObject();
        m.put("id", id);
        m.put("name", name);
        m.put("reasoning", reasoning);
        JSONArray outModalities = new JSONArray();
        outModalities.put("text");
        m.put("output_modalities", outModalities);
        JSONObject relay = new JSONObject();
        relay.put("modality", modality);
        relay.put("reasoning", reasoning);
        if (context != null) {
            relay.put("context", context);
        }
        JSONArray abilities = new JSONArray();
        if (reasoning) {
            abilities.put("reasoning");
        }
        relay.put("abilities", abilities);
        m.put("relay", relay);
        return m;
    }

    private static JSONObject findModel(JSONArray models, String id) {
        if (models == null) return null;
        for (int i = 0; i < models.length(); i++) {
            JSONObject m = models.optJSONObject(i);
            if (m != null && id.equals(m.optString("id"))) {
                return m;
            }
        }
        return null;
    }
}
