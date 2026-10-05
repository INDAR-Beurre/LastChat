package com.relay.lastlab.server;

/** Thread-safe credential persistence. Platform.setPref must commit synchronously. */
public final class GatewayKeyStore {
    private final Platform platform;
    public GatewayKeyStore(Platform platform) { this.platform = platform; }
    public synchronized String get() {
        String key = platform.getPref("gateway_key", null);
        return key == null || key.trim().isEmpty() ? null : key;
    }
    public synchronized void set(String key) {
        platform.setPref("gateway_key", key == null || key.trim().isEmpty() ? null : key.trim());
    }
    public synchronized boolean isSet() { return get() != null; }
}
