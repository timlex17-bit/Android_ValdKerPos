package com.valdker.pos.network;

import androidx.annotation.NonNull;
import com.valdker.pos.BuildConfig;
import com.valdker.pos.SessionManager;

public final class ApiConfig {

    private ApiConfig(){}

    @NonNull
    public static String base(@NonNull SessionManager session) {
        String base = session.getBaseUrl();

        if (base == null || base.trim().isEmpty()) {
            base = BuildConfig.BASE_URL; // fallback
        }

        base = base.trim();

        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }

        if (!base.endsWith("/")) {
            base = base + "/";
        }

        return base;
    }

    @NonNull
    public static String url(@NonNull SessionManager session, @NonNull String path) {
        String base = base(session);

        if (path.startsWith("/")) path = path.substring(1);

        return base + path;
    }
}