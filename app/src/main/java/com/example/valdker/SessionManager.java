package com.example.valdker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

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
 */
public class SessionManager {

    private static final String TAG = "SESSION";

    private static final String PREF_NAME = "valdker_session";

    private static final String KEY_SHIFT_OPEN = "shift_open";
    private static final String KEY_SHIFT_ID = "shift_id";
    private static final String KEY_OPENING_CASH = "opening_cash";

    private static final String KEY_SHOP_ID = "shop_id";

    private static final String KEY_TOKEN = "token";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLE = "role";
    private static final String KEY_PERMS = "perms"; // String Set

    private static final String KEY_SHIFT_LOCAL_ID = "shift_local_id";

    private final Context context;

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setShiftLocalId(long id) {
        prefs.edit().putLong(KEY_SHIFT_LOCAL_ID, id).apply();
    }

    public long getShiftLocalId() {
        return prefs.getLong(KEY_SHIFT_LOCAL_ID, -1);
    }

    public void saveSession(String token, String username, String role) {
        saveSession(token, username, role, null);
    }

    // -------------------------------
    // SHOP
    // -------------------------------
    public void setShopId(int shopId) {
        prefs.edit().putInt(KEY_SHOP_ID, shopId).apply();
    }

    public int getShopId() {
        return prefs.getInt(KEY_SHOP_ID, 1); // fallback 1 kalau belum ada
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

    public void setOpeningCash(String cash) {
        if (cash == null) cash = "0.00";
        prefs.edit().putString(KEY_OPENING_CASH, cash).apply();
    }

    public String getOpeningCash() {
        return prefs.getString(KEY_OPENING_CASH, "0.00");
    }

    public void clearShift() {
        prefs.edit()
                .putBoolean(KEY_SHIFT_OPEN, false)
                .putInt(KEY_SHIFT_ID, 0)
                .putLong(KEY_SHIFT_LOCAL_ID, 0L)
                .putString(KEY_OPENING_CASH, "0.00")
                .apply();
    }

    /**
     * Save a new user session with permissions.
     */
    public void saveSession(String token, String username, String role, JSONArray permissions) {
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

    public String getToken() {
        return prefs.getString(KEY_TOKEN, "");
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, "");
    }

    public boolean isAdmin() {
        // Our backend policy: ADMIN is_superuser.
        // Android checks role string for UI only.
        String r = getRole();
        if (r == null) return false;
        r = r.trim().toLowerCase();
        return r.equals("admin");
    }

    public Set<String> getPermissions() {
        Set<String> s = prefs.getStringSet(KEY_PERMS, new HashSet<>());
        return (s != null) ? s : new HashSet<>();
    }

    public boolean hasPermission(String code) {
        if (code == null) return false;
        return getPermissions().contains(code.trim());
    }

    public boolean isLoggedIn() {
        String token = getToken();
        return token != null && !token.trim().isEmpty();
    }

    public void clear() {
        Log.w(TAG, "Clearing session");
        prefs.edit().clear().apply();
    }

    public void logSession(String tag) {
        Log.i(tag, "username=" + getUsername());
        Log.i(tag, "role=" + getRole());
        Log.i(tag, "perms=" + getPermissions());
        Log.i(tag, "loggedIn=" + isLoggedIn());
    }
}