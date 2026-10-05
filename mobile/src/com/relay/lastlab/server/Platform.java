package com.relay.lastlab.server;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Device services used by the embedded server.
 *
 * <p>This interface exists so that every class in this package can be compiled and tested as
 * plain Java on a build machine. Android APIs are confined to {@link AndroidPlatform}.
 *
 * <p>Implementations must persist preferences synchronously (a hard kill must not lose a
 * newly-saved credential).
 */
public interface Platform {

    String getPref(String key, String def);

    void setPref(String key, String value);

    /** Returns the asset decoded as text, or {@code null} when it does not exist. */
    String readAsset(String path) throws IOException;

    /**
     * Returns the asset's raw bytes, or {@code null} when it does not exist.
     *
     * <p>The default derives the bytes from {@link #readAsset(String)} and is therefore only
     * correct for textual assets. It is correct for {@code .html}, {@code .js}, {@code .css},
     * {@code .json} and {@code .svg}, and WRONG for binary assets: decoding PNG or woff2 bytes
     * as UTF-8 replaces every invalid sequence with U+FFFD, irreversibly corrupting them.
     * {@link AndroidPlatform} overrides this to read the asset stream directly.
     */
    default byte[] readAssetBytes(String path) throws IOException {
        String text = readAsset(path);
        return text == null ? null : text.getBytes(StandardCharsets.UTF_8);
    }

    void log(String tag, String msg, Throwable t);

    /** Names (not paths) of the entries in the given directory relative to {@link #getFilesDir()}. */
    List<String> listFiles(String dirRelative);

    File getFilesDir();
}