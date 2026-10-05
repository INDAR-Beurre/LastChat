package com.relay.lastlab.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/** Small JSON-file-backed store for conversations, messages and settings. */
final class JsonStore {

    private static final String ROOT = "lastlab";

    private final Platform platform;
    private final File dir;

    JsonStore(Platform platform) {
        this.platform = platform;
        this.dir = new File(platform.getFilesDir(), ROOT);
    }

    File dir() {
        return dir;
    }

    JSONObject read(String name) {
        File f = new File(dir, name);
        if (!f.isFile()) {
            return null;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] buf = new byte[(int) Math.max(64, f.length())];
            int n = readFully(in, buf);
            if (n <= 0) {
                return null;
            }
            return new JSONObject(new String(buf, 0, n, "UTF-8"));
        } catch (Exception e) {
            platform.log("store", "read failed: " + name, e);
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    void write(String name, JSONObject value) {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            platform.log("store", "cannot create " + dir, null);
            return;
        }
        File tmp = new File(dir, name + ".tmp");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tmp);
            out.write(value.toString().getBytes("UTF-8"));
            out.flush();
            File target = new File(dir, name);
            if (target.exists() && !target.delete()) {
                platform.log("store", "cannot replace " + name, null);
                return;
            }
            if (!tmp.renameTo(target)) {
                platform.log("store", "cannot rename into place " + name, null);
            }
        } catch (Exception e) {
            platform.log("store", "write failed: " + name, e);
        } finally {
            closeQuietly(out);
        }
    }

    void delete(String name) {
        File f = new File(dir, name);
        if (f.isFile() && !f.delete()) {
            platform.log("store", "delete failed: " + name, null);
        }
    }

    List<String> listNames() {
        List<String> out = new ArrayList<String>();
        String[] names = dir.list();
        if (names == null) {
            return out;
        }
        for (String n : names) {
            if (!n.endsWith(".tmp")) {
                out.add(n);
            }
        }
        java.util.Collections.sort(out);
        return out;
    }

    private static int readFully(FileInputStream in, byte[] buf) throws IOException {
        int total = 0;
        int n;
        while (total < buf.length && (n = in.read(buf, total, buf.length - total)) != -1) {
            total += n;
        }
        return total;
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    static JSONArray toArray(List<String> values) {
        JSONArray arr = new JSONArray();
        if (values != null) {
            for (String v : values) {
                arr.put(v);
            }
        }
        return arr;
    }
}
