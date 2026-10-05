package com.relay.lastlab.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Platform} backed by Android.
 *
 * <p>This is the only class in the server package permitted to import {@code android.*}, which
 * keeps every other class compilable — and testable — as plain Java.
 */
public final class AndroidPlatform implements Platform {

    private static final String PREFS = "lastlab";
    private static final String TAG = "LastLab";

    private final Context context;
    private final SharedPreferences prefs;

    public AndroidPlatform(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public String getPref(String key, String def) {
        try {
            return prefs.getString(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public void setPref(String key, String value) {
        try {
            // commit() rather than apply(): a hard kill must not lose a saved credential.
            prefs.edit().putString(key, value).commit();
        } catch (Exception e) {
            log(TAG, "setPref failed: " + key, e);
        }
    }

    @Override
    public String readAsset(String path) throws IOException {
        byte[] bytes = readAssetBytes(path);
        return bytes == null ? null : new String(bytes, Http.UTF_8);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overridden because the default derives bytes from UTF-8 decoding, which corrupts binary
     * assets such as favicon.png and the webfonts.
     */
    @Override
    public byte[] readAssetBytes(String path) throws IOException {
        InputStream in;
        try {
            in = context.getAssets().open(path);
        } catch (IOException absent) {
            return null; // a missing asset is normal, not an error
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, in.available()));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            closeQuietly(in);
        }
    }

    @Override
    public void log(String tag, String msg, Throwable t) {
        if (t != null) {
            Log.e(tag == null ? TAG : tag, msg == null ? "" : msg, t);
        } else {
            Log.i(tag == null ? TAG : tag, msg == null ? "" : msg);
        }
    }

    @Override
    public List<String> listFiles(String dirRelative) {
        List<String> names = new ArrayList<String>();
        File dir = dirRelative == null || dirRelative.isEmpty()
                ? getFilesDir()
                : new File(getFilesDir(), dirRelative);
        String[] entries = dir.list();
        if (entries == null) {
            return names;
        }
        for (String name : entries) {
            names.add(name);
        }
        return names;
    }

    @Override
    public File getFilesDir() {
        return context.getFilesDir();
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
}