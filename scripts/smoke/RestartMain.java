package smoke;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.relay.lastlab.server.LoopbackServer;

/**
 * Proves conversations survive a restart, which a file-backed store must do.
 *
 * <p>Exits non-zero on failure so the build gate fails loudly.
 */
public final class RestartMain {

    private static int checks = 0;
    private static final List<String> failures = new ArrayList<String>();

    public static void main(String[] args) throws Exception {
        FakeRelay relay = new FakeRelay();
        try {
            relay.catalog("{\"data\":[{\"id\":\"m1\",\"name\":\"M\",\"output_modalities\":[\"text\"],"
                    + "\"relay\":{\"modality\":\"text\",\"context\":1000}}]}");
            relay.stream("{\"choices\":[{\"delta\":{\"content\":\"survives restart\"}}]}",
                    "{\"choices\":[{\"delta\":{}}],\"usage\":{\"prompt_tokens\":3,"
                            + "\"completion_tokens\":4}}");

            SmokeMain.MemPlatform platform = new SmokeMain.MemPlatform();
            platform.clearPref("gateway_key");

            String conversationId;
            // First run: send a message and let generation finish.
            LoopbackServer first = new LoopbackServer(platform, relay.baseUrl());
            first.start();
            try {
                SmokeMain.setKey(first.getBaseUrl(), "sk-restart-key");
                JSONObject created = new JSONObject(
                        SmokeMain.raw(first.getBaseUrl(), "POST", "/api/conversations", "{}"));
                conversationId = created.getString("id");

                SmokeMain.StreamCapture capture = new SmokeMain.StreamCapture(
                        first.getBaseUrl(),
                        "/api/conversations/" + conversationId + "/stream");
                SmokeMain.raw(first.getBaseUrl(), "POST",
                        "/api/conversations/" + conversationId + "/messages",
                        "{\"parts\":[{\"type\":\"text\",\"text\":\"remember me\"}],"
                                + "\"modelId\":\"auto\"}");
                List<JSONObject> events = capture.finish(20000);
                check("generation completed before restart",
                        SmokeMain.lastEventGenerating(events) == false,
                        "last event still generating");
            } finally {
                first.stop();
            }

            // Second run: the same platform, a brand new server.
            LoopbackServer second = new LoopbackServer(platform, relay.baseUrl());
            second.start();
            try {
                JSONObject reloaded = SmokeMain.getJson(
                        second.getBaseUrl() + "/api/conversations/" + conversationId);
                check("conversation is listed after restart",
                        SmokeMain.getJson(second.getBaseUrl() + "/api/conversations/paged?offset=0&limit=50")
                                .optJSONArray("items").length() == 1,
                        "conversation missing from the list");

                String text = SmokeMain.assistantTextFromNodes(reloaded.optJSONArray("messages"));
                check("assistant reply survived the restart",
                        "survives restart".equals(text), text);

                JSONObject assistant = lastAssistantMessage(reloaded.optJSONArray("messages"));
                check("usage survived the restart",
                        assistant != null
                                && assistant.optJSONObject("usage").optInt("promptTokens", -1) == 3,
                        String.valueOf(assistant));
                check("turn is marked finished",
                        assistant != null && assistant.has("finishedAt"),
                        String.valueOf(assistant));

                check("gateway key survived the restart",
                        SmokeMain.getJson(second.getBaseUrl() + "/api/health")
                                .optBoolean("gatewayConfigured"),
                        "gatewayConfigured=false");
            } finally {
                second.stop();
            }
        } finally {
            relay.close();
        }

        System.out.println();
        System.out.println("checks run: " + checks);
        if (failures.isEmpty()) {
            System.out.println("RESTART PASS");
            System.exit(0);
        }
        System.out.println("RESTART FAIL (" + failures.size() + ")");
        for (String f : failures) {
            System.out.println("  - " + f);
        }
        System.exit(1);
    }

    private static JSONObject lastAssistantMessage(JSONArray nodes) {
        JSONObject found = null;
        if (nodes == null) {
            return null;
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
                if (message != null && "ASSISTANT".equals(message.optString("role"))) {
                    found = message;
                }
            }
        }
        return found;
    }

    private static void check(String name, boolean ok, String detail) {
        checks++;
        System.out.println((ok ? "PASS  " : "FAIL  ") + name + (ok ? "" : "  [" + detail + "]"));
        if (!ok) {
            failures.add(name + "  [" + detail + "]");
        }
    }
}