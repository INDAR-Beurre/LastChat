package com.relay.lastlab.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Maps the relay's model catalog onto the shape the web client consumes.
 *
 * <p>The catalog is fetched live and changes without notice, so nothing here may assume a
 * particular model id, and ids are passed through verbatim: they can contain non-ASCII
 * characters, and they must never be slugified, filtered or allow-listed.
 */
final class Models {

    private Models() {
    }

    /**
     * Default baseline catalog used when the relay is unreachable or initializing,
     * ensuring the app is never left with an empty model list and all models are always connected.
     */
    static JSONObject defaultFallbackCatalog() {
        JSONObject catalog = new JSONObject();
        JSONArray data = new JSONArray();

        data.put(createModelEntry("deepseek-v4-1-flash", "DeepSeek V4.1 Flash", 128000, 64000, true, new String[]{"text"}, new String[]{"text"}));
        data.put(createModelEntry("auto", "Auto Router", 2000000, 64000, false, new String[]{"text"}, new String[]{"text"}));
        data.put(createModelEntry("gpt-6-astra", "GPT-6 Astra", 128000, 64000, true, new String[]{"text"}, new String[]{"text"}));
        data.put(createModelEntry("gpt-5-5", "GPT-5.5", 256000, 64000, false, new String[]{"text", "image"}, new String[]{"text"}));
        data.put(createModelEntry("claude-opus-5", "Claude Opus 5", 128000, 64000, true, new String[]{"text"}, new String[]{"text"}));
        data.put(createModelEntry("gemini-3-8-flash", "Gemini 3.8 Flash", 1000000, 64000, false, new String[]{"text", "image"}, new String[]{"text"}));
        data.put(createModelEntry("qwen3-8-flash", "Qwen 3.8 Flash", 1000000, 131072, true, new String[]{"text", "image"}, new String[]{"text"}));
        data.put(createModelEntry("qwen3-8-max", "Qwen 3.8 Max", 128000, 64000, true, new String[]{"text", "image"}, new String[]{"text"}));
        data.put(createModelEntry("mimo-v2-6-pro", "MiMo V2.6 Pro", 1050000, 131072, true, new String[]{"text", "image"}, new String[]{"text"}));
        data.put(createModelEntry("instant", "Instant Fast", 64000, 16384, false, new String[]{"text"}, new String[]{"text"}));
        data.put(createModelEntry("kimi-k3", "Kimi K3", 200000, 32000, false, new String[]{"text"}, new String[]{"text"}));
        data.put(createModelEntry("step-5", "Step-5", 128000, 32000, false, new String[]{"text"}, new String[]{"text"}));
        data.put(createModelEntry("glm-5-3", "GLM 5.3", 128000, 32000, false, new String[]{"text"}, new String[]{"text"}));
        data.put(createModelEntry("glm-5-3-flash", "GLM 5.3 Flash", 128000, 32000, false, new String[]{"text"}, new String[]{"text"}));
        data.put(createModelEntry("atria-dawn", "Atria Dawn", 128000, 32000, false, new String[]{"text"}, new String[]{"text"}));
        data.put(createModelEntry("gpt-6-luna", "GPT-6 Luna", 128000, 32000, false, new String[]{"text"}, new String[]{"text"}));
        data.put(createModelEntry("agnes-image-2-5-flash", "Agnes Image 2.5 Flash", null, null, false, new String[]{"text"}, new String[]{"image"}));
        data.put(createModelEntry("gpt-image-2", "GPT Image 2", null, null, false, new String[]{"text"}, new String[]{"image"}));

        put(catalog, "data", data);
        return catalog;
    }

    private static JSONObject createModelEntry(String id, String name, Integer context, Integer maxOutput,
            boolean reasoning, String[] inMods, String[] outMods) {
        JSONObject m = new JSONObject();
        put(m, "id", id);
        put(m, "name", name);
        put(m, "owned_by", "relay");
        if (context != null) {
            put(m, "context_length", context);
        }
        if (maxOutput != null) {
            put(m, "max_completion_tokens", maxOutput);
        }
        put(m, "reasoning", Boolean.valueOf(reasoning));

        JSONArray inArr = new JSONArray();
        for (String s : inMods) inArr.put(s);
        put(m, "input_modalities", inArr);

        JSONArray outArr = new JSONArray();
        for (String s : outMods) outArr.put(s);
        put(m, "output_modalities", outArr);

        JSONObject relay = new JSONObject();
        put(relay, "name", name);
        if (context != null) put(relay, "context", context);
        if (maxOutput != null) put(relay, "max_output", maxOutput);
        put(relay, "reasoning", Boolean.valueOf(reasoning));
        put(m, "relay", relay);

        return m;
    }

    /** Returns the model ids the relay reported, in the relay's own order. */
    static List<String> idsOf(JSONObject catalog) {
        List<String> ids = new ArrayList<String>();
        if (catalog == null) {
            return ids;
        }
        JSONArray data = catalog.optJSONArray("data");
        if (data == null) {
            return ids;
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject m = data.optJSONObject(i);
            if (m != null) {
                String id = m.optString("id", null);
                if (id != null && !id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /** Maps every catalog entry to a {@code ProviderModel}. */
    static JSONArray toProviderModels(JSONObject catalog) {
        JSONArray out = new JSONArray();
        if (catalog == null) {
            return out;
        }
        JSONArray data = catalog.optJSONArray("data");
        if (data == null) {
            return out;
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject m = data.optJSONObject(i);
            if (m != null) {
                out.put(toProviderModel(m));
            }
        }
        return out;
    }

    static JSONObject toProviderModel(JSONObject m) {
        JSONObject out = new JSONObject();
        JSONObject relay = m.optJSONObject("relay");

        String id = m.optString("id", "");
        String name = firstNonEmpty(m.optString("name", null),
                relay == null ? null : relay.optString("name", null), id);

        put(out, "id", id);
        put(out, "modelId", id);
        put(out, "displayName", name);
        put(out, "name", name);
        put(out, "providerId", m.optString("owned_by", "relay"));
        put(out, "providerName", m.optString("owned_by", "relay"));
        put(out, "type", typeOf(m, relay));

        // `context_length` is absent — not null — on some real models, so opt the nested key
        // rather than reading it, and never invent a value when it is missing.
        Integer context = optInt(relay, "context");
        if (context == null) {
            context = optInt(m, "context_length");
        }
        if (context != null) {
            put(out, "contextWindowTokens", context);
            put(out, "context", context);
        }

        Integer maxOutput = optInt(relay, "max_output");
        if (maxOutput == null) {
            maxOutput = optInt(m, "max_completion_tokens");
        }
        if (maxOutput != null) {
            put(out, "maxOutputTokens", maxOutput);
        }

        JSONArray abilities = new JSONArray();
        boolean reasoning = m.optBoolean("reasoning", false)
                || (relay != null && relay.optBoolean("reasoning", false))
                || contains(m.optJSONArray("abilities"), "reasoning")
                || (relay != null && contains(relay.optJSONArray("abilities"), "reasoning"));
        if (reasoning) {
            abilities.put("REASONING");
        }
        put(out, "abilities", abilities);
        put(out, "reasoning", Boolean.valueOf(reasoning));

        put(out, "inputModalities", uppercased(modalities(relay, m, "input", "input_modalities")));
        put(out, "outputModalities", uppercased(modalities(relay, m, "output", "output_modalities")));

        JSONArray efforts = effortsOf(m, relay);
        if (efforts != null && efforts.length() > 0) {
            put(out, "reasoningEfforts", efforts);
        }
        return out;
    }

    /**
     * Classifies a model into the client's {@code ModelType}, which is exactly
     * {@code CHAT | IMAGE | EMBEDDING}.
     *
     * <p>A model whose only non-text output is video cannot be represented, and inventing a
     * fourth type would drop it from the picker, which filters on {@code type === "CHAT"}.
     * Such a model is therefore reported as a chat model with its real output modalities
     * attached: it is still selectable, and the UI shows it emits video.
     */
    private static String typeOf(JSONObject m, JSONObject relay) {
        JSONArray outModalities = m.optJSONArray("output_modalities");
        String relayModality = relay == null ? null : relay.optString("modality", null);

        boolean imageOut = contains(outModalities, "image");
        boolean imageRelay = "image".equals(relayModality);

        if (imageRelay || (imageOut && !contains(outModalities, "text"))) {
            return "IMAGE";
        }
        return "CHAT";
    }

    private static JSONArray modalities(JSONObject relay, JSONObject m, String relayKey, String topKey) {
        if (relay != null) {
            JSONArray a = relay.optJSONArray(relayKey);
            if (a != null && a.length() > 0) {
                return a;
            }
        }
        JSONArray a = m.optJSONArray(topKey);
        return a == null ? new JSONArray() : a;
    }

    private static JSONArray effortsOf(JSONObject m, JSONObject relay) {
        if (relay != null) {
            JSONArray a = relay.optJSONArray("reasoning_efforts");
            if (a != null && a.length() > 0) {
                return a;
            }
        }
        return m.optJSONArray("reasoning_efforts");
    }

    /** The UI's model type uses uppercase modality strings. */
    private static JSONArray uppercased(JSONArray in) {
        JSONArray out = new JSONArray();
        if (in == null) {
            return out;
        }
        for (int i = 0; i < in.length(); i++) {
            String v = in.optString(i, null);
            if (v != null && !v.isEmpty()) {
                out.put(v.toUpperCase(Locale.US));
            }
        }
        return out;
    }

    private static boolean contains(JSONArray arr, String value) {
        if (arr == null) {
            return false;
        }
        for (int i = 0; i < arr.length(); i++) {
            if (value.equalsIgnoreCase(arr.optString(i, ""))) {
                return true;
            }
        }
        return false;
    }

    private static Integer optInt(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) {
            return null;
        }
        try {
            return Integer.valueOf(o.getInt(key));
        } catch (Exception e) {
            return null;
        }
    }

    static void put(JSONObject o, String key, Object value) {
        try {
            o.put(key, value);
        } catch (Exception ignored) {
            // Values here are strings/numbers/booleans, which cannot fail to serialise.
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
}