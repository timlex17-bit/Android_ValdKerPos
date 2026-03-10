package com.example.valdker;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.valdker.BuildConfig;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;

import java.util.HashSet;
import java.util.Set;

/**
 * SessionManager
 *
 * Stores user session data:
 * - token
 * - username
 * - role
 * - permissions (string set)
 * - shift state (open flag, shift id, opening cash)
 * - shop id
 */
public class SessionManager {

    private static final String TAG = "SESSION";
    private static final String PREF_NAME = "valdker_session";

    private static final String KEY_BASE_URL = "base_url";

    private static final String KEY_SHIFT_OPEN = "shift_open";
    private static final String KEY_SHIFT_ID = "shift_id";
    private static final String KEY_OPENING_CASH = "opening_cash";
    private static final String KEY_SHIFT_LOCAL_ID = "shift_local_id";

    private static final String KEY_SHOP_ID = "shop_id";

    private static final String KEY_TOKEN = "token";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLE = "role";
    private static final String KEY_PERMS = "perms";

    private final SharedPreferences prefs;

    public SessionManager(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // -------------------------------
    // SHIFT LOCAL ID (legacy / compatibility)
    // -------------------------------
    public void setShiftLocalId(long id) {
        prefs.edit().putLong(KEY_SHIFT_LOCAL_ID, id).apply();
    }

    public long getShiftLocalId() {
        return prefs.getLong(KEY_SHIFT_LOCAL_ID, -1);
    }

    // -------------------------------
    // SHOP
    // -------------------------------
    public void setShopId(int shopId) {
        prefs.edit().putInt(KEY_SHOP_ID, shopId).apply();
    }

    public int getShopId() {
        return prefs.getInt(KEY_SHOP_ID, 1);
    }

    // -------------------------------
    // SHIFT (Cashier must open shift)
    // -------------------------------
    public void setShiftOpen(boolean open) {
        prefs.edit().putBoolean(KEY_SHIFT_OPEN, open).apply();
    }

    public boolean isShiftOpen() {
        return prefs.getBoolean(KEY_SHIFT_OPEN, false);
    }

    public void setShiftId(int id) {
        prefs.edit().putInt(KEY_SHIFT_ID, id).apply();
    }

    public int getShiftId() {
        return prefs.getInt(KEY_SHIFT_ID, 0);
    }

    public void setOpeningCash(@Nullable String cash) {
        if (cash == null) cash = "0.00";
        prefs.edit().putString(KEY_OPENING_CASH, cash).apply();
    }

    @NonNull
    public String getOpeningCash() {
        String v = prefs.getString(KEY_OPENING_CASH, "0.00");
        return v != null ? v : "0.00";
    }

    public void clearShift() {
        prefs.edit()
                .putBoolean(KEY_SHIFT_OPEN, false)
                .putInt(KEY_SHIFT_ID, 0)
                .putLong(KEY_SHIFT_LOCAL_ID, -1L)
                .putString(KEY_OPENING_CASH, "0.00")
                .apply();
    }

    // -------------------------------
    // SESSION
    // -------------------------------
    public void saveSession(@Nullable String token, @Nullable String username, @Nullable String role) {
        saveSession(token, username, role, null);
    }

    /**
     * Saves a new user session with permissions.
     */
    public void saveSession(@Nullable String token,
                            @Nullable String username,
                            @Nullable String role,
                            @Nullable JSONArray permissions) {

        if (token == null) token = "";
        if (username == null) username = "";
        if (role == null) role = "";

        Log.i(TAG, "Saving session (username=" + username + ", role=" + role + ")");

        Set<String> permSet = new HashSet<>();
        if (permissions != null) {
            for (int i = 0; i < permissions.length(); i++) {
                String p = permissions.optString(i, "").trim();
                if (!p.isEmpty()) permSet.add(p);
            }
        }

        prefs.edit()
                .putString(KEY_TOKEN, token.trim())
                .putString(KEY_USERNAME, username.trim())
                .putString(KEY_ROLE, role.trim())
                .putStringSet(KEY_PERMS, permSet)
                .apply();
    }

    @NonNull
    public String getToken() {
        String v = prefs.getString(KEY_TOKEN, "");
        return v != null ? v : "";
    }

    @NonNull
    public String getUsername() {
        String v = prefs.getString(KEY_USERNAME, "");
        return v != null ? v : "";
    }

    @NonNull
    public String getRole() {
        String v = prefs.getString(KEY_ROLE, "");
        return v != null ? v : "";
    }

    public boolean isAdmin() {
        return "admin".equals(getRole().trim().toLowerCase());
    }

    @NonNull
    public Set<String> getPermissions() {
        Set<String> s = prefs.getStringSet(KEY_PERMS, new HashSet<>());
        return (s != null) ? s : new HashSet<>();
    }

    public boolean hasPermission(@Nullable String code) {
        if (code == null) return false;
        return getPermissions().contains(code.trim());
    }

    public boolean isLoggedIn() {
        return !getToken().trim().isEmpty();
    }

    public void clear() {
        Log.w(TAG, "Clearing session");
//        prefs.edit().clear().apply();
    }

    public void logSession(@NonNull String tag) {
        Log.i(tag, "username=" + getUsername());
        Log.i(tag, "role=" + getRole());
        Log.i(tag, "perms=" + getPermissions());
        Log.i(tag, "loggedIn=" + isLoggedIn());
    }

    public void clearAuth() {
        Log.w("SESSION", "Clearing auth only");

        SharedPreferences.Editor editor = prefs.edit();

        editor.remove(KEY_TOKEN);
        editor.remove(KEY_USERNAME);
        editor.remove(KEY_ROLE);

        editor.apply();
    }

    public void setBaseUrl(@NonNull String url) {
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        prefs.edit().putString(KEY_BASE_URL, u).apply();
    }

    public void clearBaseUrl() {
        prefs.edit().remove(KEY_BASE_URL).apply();
    }

    @NonNull
    public String getBaseUrl() {
        String saved = prefs.getString(KEY_BASE_URL, null);

        if (saved != null && !saved.trim().isEmpty()) {
            return ensureTrailingSlash(saved);
        }

        return ensureTrailingSlash(BuildConfig.BASE_URL);
    }

    private String ensureTrailingSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    public void clearToken() {
    }
}