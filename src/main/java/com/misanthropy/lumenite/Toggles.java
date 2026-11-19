package com.misanthropy.lumenite;

public final class Toggles {
    public static volatile boolean fastSkySources = !Boolean.getBoolean("lumenite.vanillaSkySources");

    private Toggles() {
    }
}
