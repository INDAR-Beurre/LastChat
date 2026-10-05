package com.relay.lastlab.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Small HTTP helpers shared by the loopback server and the relay client. */
final class Http {

    private static final int MAX_BODY = 32 * 1024 * 1024;
    static final java.nio.charset.Charset ISO_8859_1 = java.nio.charset.Charset.forName("ISO-8859-1");
    static final java.nio.charset.Charset UTF_8 = java.nio.charset.StandardCharsets.UTF_8;

    private Http() {
    }

    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return out;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            out.put(urlDecode(name), urlDecode(value));
        }
        return out;
    }

    static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    static byte[] iso88591(String s) {
        return s.getBytes(ISO_8859_1);
    }

    /**
     * Reads a body as ISO-8859-1, which maps bytes 0x00-0xFF one-to-one onto chars and back.
     * UTF-8 would be lossy for binary payloads (multipart uploads), so bodies are carried as
     * ISO-8859-1 text and re-encoded with the same charset on the way out.
     */
    static String readBody(Reader reader, String contentLength) throws IOException {
        if (reader == null) {
            return "";
        }
        int limit = MAX_BODY;
        if (contentLength != null) {
            try {
                long declared = Long.parseLong(contentLength.trim());
                if (declared >= 0) {
                    limit = (int) Math.min(MAX_BODY, declared);
                }
            } catch (NumberFormatException ignored) {
                // Absent or bogus Content-Length: read until EOF, bounded by MAX_BODY.
            }
        }
        char[] buf = new char[8192];
        StringBuilder out = new StringBuilder(Math.min(limit, 8192));
        while (out.length() < limit) {
            int n = reader.read(buf, 0, Math.min(buf.length, limit - out.length()));
            if (n == -1) {
                break;
            }
            out.append(buf, 0, n);
        }
        return out.toString();
    }

    static String readStream(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), UTF_8);
    }

    static String mimeFor(String path) {
        String p = path.toLowerCase(Locale.US);
        if (p.endsWith(".html") || p.endsWith(".htm")) {
            return "text/html; charset=utf-8";
        }
        if (p.endsWith(".js") || p.endsWith(".mjs")) {
            return "text/javascript; charset=utf-8";
        }
        if (p.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (p.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (p.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (p.endsWith(".png")) {
            return "image/png";
        }
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (p.endsWith(".webp")) {
            return "image/webp";
        }
        if (p.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (p.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (p.endsWith(".woff")) {
            return "font/woff";
        }
        if (p.endsWith(".woff3")) {
            return "font/woff3";
        }
        if (p.endsWith(".ttf")) {
            return "font/ttf";
        }
        if (p.endsWith(".txt") || p.endsWith(".map")) {
            return "text/plain; charset=utf-8";
        }
        return "application/octet-stream";
    }
}