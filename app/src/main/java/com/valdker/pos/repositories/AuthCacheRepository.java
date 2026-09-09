package com.valdker.pos.repositories;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.SessionManager;
import com.valdker.pos.local.CachedMenuPermissionEntity;
import com.valdker.pos.local.CachedRoleEntity;
import com.valdker.pos.local.CachedUserEntity;
import com.valdker.pos.local.ValoraLocalDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthCacheRepository {

    private static final String TAG = "AUTH_CACHE";

    public interface Callback {
        void onApplied();
        void onMissing();
        void onError(@NonNull String message);
    }

    private final Context appContext;
    private final SessionManager sessionManager;
    private final ValoraLocalDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AuthCacheRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        sessionManager = new SessionManager(appContext);
        db = ValoraLocalDatabase.getInstance(appContext);
    }

    public void saveCurrentSessionFromLogin(@NonNull JSONObject loginResponse) {
        executor.execute(() -> {
            try {
                if (!hasKnownShop() || TextUtils.isEmpty(sessionManager.getUsername())) {
                    Log.w(TAG, "Skipping auth cache save. Unknown shop/user.");
                    return;
                }

                long now = System.currentTimeMillis();
                JSONObject userJson = loginResponse.optJSONObject("user");
                JSONObject shopJson = loginResponse.optJSONObject("shop");
                JSONArray permissions = loginResponse.optJSONArray("permissions");
                JSONArray effectiveModules = optJsonArrayFlexible(loginResponse, "effective_modules");
                if (effectiveModules == null && userJson != null) {
                    effectiveModules = optJsonArrayFlexible(userJson, "effective_modules");
                }
                if (effectiveModules == null && shopJson != null) {
                    effectiveModules = optJsonArrayFlexible(shopJson, "effective_modules");
                }
                JSONObject menuPermissions = optJsonObjectFlexible(loginResponse, "menu_permissions");
                if (menuPermissions == null && userJson != null) {
                    menuPermissions = optJsonObjectFlexible(userJson, "menu_permissions");
                }
                if (menuPermissions == null) {
                    menuPermissions = sessionManager.getMenuPermissions();
                }

                CachedUserEntity user = new CachedUserEntity();
                user.backendId = userJson != null ? userJson.optInt("id", 0) : 0;
                user.shopId = currentShopId();
                user.shopCode = currentShopCode();
                user.apiBaseUrl = currentApiBaseUrl();
                user.username = sessionManager.getUsername();
                user.fullName = sessionManager.getFullName();
                user.email = userJson != null ? userJson.optString("email", "") : "";
                user.role = sessionManager.getRole();
                user.shopName = sessionManager.getShopName();
                user.businessType = sessionManager.getBusinessType();
                user.plan = sessionManager.getPlan();
                user.effectiveModulesJson = effectiveModules != null ? effectiveModules.toString() : "[]";
                user.isActive = userJson == null || userJson.optBoolean("is_active", true);
                user.lastLogin = userJson != null ? userJson.optString("last_login", "") : "";
                user.syncedAt = formatIso(now);
                user.permissionsJson = permissions != null ? permissions.toString() : permissionsFromSession().toString();
                user.menuPermissionsJson = menuPermissions != null ? menuPermissions.toString() : "{}";
                user.featuresJson = sessionManager.getShopFeatures().toString();
                user.isSuperuser = sessionManager.isPlatformSuperuser();
                user.isPlatformAdmin = sessionManager.isPlatformAdmin();
                user.isShopOwner = sessionManager.isShopOwner();
                user.isShopManager = sessionManager.isShopManager();
                user.isShopCashier = sessionManager.isShopCashier();
                user.rawJson = loginResponse.toString();
                user.lastSyncAt = now;
                user.cacheKey = userCacheKey(user.username);

                CachedRoleEntity role = new CachedRoleEntity();
                role.backendId = userJson != null ? userJson.optInt("role_id", 0) : 0;
                role.shopId = currentShopId();
                role.shopCode = currentShopCode();
                role.apiBaseUrl = currentApiBaseUrl();
                role.role = user.role;
                role.name = user.role;
                role.description = userJson != null ? userJson.optString("role_name", "") : "";
                role.rawJson = roleJson(role).toString();
                role.lastSyncAt = now;
                role.cacheKey = roleCacheKey(role.role);

                db.cachedUserDao().upsertUserAndRole(user, role);
                db.cachedMenuPermissionDao().replaceForUser(
                        currentShopId(),
                        currentShopCode(),
                        currentApiBaseUrl(),
                        user.username,
                        buildMenuPermissionEntities(user, menuPermissions, now)
                );
                Log.i(TAG, "Cached auth permissions for " + user.username + " shop=" + user.shopId + "/" + user.shopCode);
            } catch (Exception e) {
                Log.e(TAG, "Failed to cache auth permissions", e);
            }
        });
    }

    public void applyCachedSessionForCurrentUser(@NonNull Callback callback) {
        executor.execute(() -> {
            try {
                if (TextUtils.isEmpty(sessionManager.getToken())
                        || TextUtils.isEmpty(sessionManager.getUsername())
                        || !hasKnownShop()) {
                    postMissing(callback);
                    return;
                }

                CachedUserEntity user = db.cachedUserDao().getUserForSession(
                        currentShopId(),
                        currentShopCode(),
                        currentApiBaseUrl(),
                        sessionManager.getUsername()
                );
                if (user == null || TextUtils.isEmpty(user.menuPermissionsJson)) {
                    postMissing(callback);
                    return;
                }

                JSONObject menuPermissions = new JSONObject(user.menuPermissionsJson);
                if (menuPermissions.length() == 0) {
                    postMissing(callback);
                    return;
                }

                JSONArray permissions = new JSONArray(TextUtils.isEmpty(user.permissionsJson) ? "[]" : user.permissionsJson);
                JSONObject features = new JSONObject(TextUtils.isEmpty(user.featuresJson) ? "{}" : user.featuresJson);
                sessionManager.applyCachedUserProfile(
                        user.username,
                        user.fullName,
                        user.role,
                        user.shopId,
                        user.shopCode,
                        user.shopName,
                        user.isSuperuser,
                        user.isPlatformAdmin,
                        user.isShopOwner,
                        user.isShopManager,
                        user.isShopCashier,
                        !TextUtils.isEmpty(user.businessType) ? user.businessType : sessionManager.getBusinessType(),
                        !TextUtils.isEmpty(user.plan) ? user.plan : sessionManager.getPlan(),
                        new JSONArray(TextUtils.isEmpty(user.effectiveModulesJson) ? "[]" : user.effectiveModulesJson),
                        features,
                        permissions,
                        menuPermissions
                );
                postApplied(callback);
            } catch (Exception e) {
                postError(callback, e.getMessage() != null ? e.getMessage() : "Failed to load cached permissions.");
            }
        });
    }

    @NonNull
    private List<CachedMenuPermissionEntity> buildMenuPermissionEntities(@NonNull CachedUserEntity user,
                                                                         @Nullable JSONObject menuPermissions,
                                                                         long syncAt) {
        List<CachedMenuPermissionEntity> out = new ArrayList<>();
        if (menuPermissions == null) {
            return out;
        }
        Iterator<String> keys = menuPermissions.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (TextUtils.isEmpty(key)) continue;
            boolean canView = parseBoolean(menuPermissions.opt(key));
            CachedMenuPermissionEntity entity = new CachedMenuPermissionEntity();
            entity.shopId = user.shopId;
            entity.shopCode = user.shopCode;
            entity.apiBaseUrl = user.apiBaseUrl;
            entity.userId = user.backendId;
            entity.username = user.username;
            entity.role = user.role;
            entity.menuKey = key.trim();
            entity.canView = canView;
            entity.enabled = canView;
            JSONObject raw = new JSONObject();
            put(raw, "menu_key", entity.menuKey);
            put(raw, "can_view", canView);
            entity.rawJson = raw.toString();
            entity.lastSyncAt = syncAt;
            entity.cacheKey = permissionCacheKey(entity.username, entity.menuKey);
            out.add(entity);
        }
        return out;
    }

    @NonNull
    private JSONArray permissionsFromSession() {
        JSONArray arr = new JSONArray();
        for (String permission : sessionManager.getPermissions()) {
            arr.put(permission);
        }
        return arr;
    }

    private boolean hasKnownShop() {
        return currentShopId() > 0 && !currentShopCode().isEmpty();
    }

    private int currentShopId() {
        return sessionManager.getShopId();
    }

    @NonNull
    private String currentShopCode() {
        return safe(sessionManager.getShopCode()).trim().toUpperCase(Locale.US);
    }

    @NonNull
    private String currentApiBaseUrl() {
        return safe(sessionManager.getBaseUrl());
    }

    @NonNull
    private String userCacheKey(@NonNull String username) {
        return currentApiBaseUrl() + "|" + currentShopId() + "|" + currentShopCode() + "|user|" + username.trim().toLowerCase(Locale.US);
    }

    @NonNull
    private String roleCacheKey(@NonNull String role) {
        return currentApiBaseUrl() + "|" + currentShopId() + "|" + currentShopCode() + "|role|" + safe(role).trim().toLowerCase(Locale.US);
    }

    @NonNull
    private String permissionCacheKey(@NonNull String username, @NonNull String menuKey) {
        return currentApiBaseUrl() + "|" + currentShopId() + "|" + currentShopCode() + "|permission|" + username.trim().toLowerCase(Locale.US) + "|" + menuKey.trim();
    }

    private void postApplied(@NonNull Callback callback) {
        mainHandler.post(callback::onApplied);
    }

    private void postMissing(@NonNull Callback callback) {
        mainHandler.post(callback::onMissing);
    }

    private void postError(@NonNull Callback callback, @NonNull String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    @Nullable
    private static JSONObject optJsonObjectFlexible(@Nullable JSONObject parent, @NonNull String key) {
        if (parent == null) return null;
        Object value = parent.opt(key);
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.isEmpty()) return null;
            try {
                return new JSONObject(raw);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean parseBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        String raw = value != null ? String.valueOf(value).trim() : "";
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    @Nullable
    private static JSONArray optJsonArrayFlexible(@Nullable JSONObject parent, @NonNull String key) {
        if (parent == null) return null;
        Object value = parent.opt(key);
        if (value instanceof JSONArray) {
            return (JSONArray) value;
        }
        if (value instanceof JSONObject) {
            JSONArray arr = new JSONArray();
            Iterator<String> keys = ((JSONObject) value).keys();
            while (keys.hasNext()) {
                String module = keys.next();
                if (parseBoolean(((JSONObject) value).opt(module))) {
                    arr.put(module);
                }
            }
            return arr;
        }
        if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.isEmpty()) return null;
            try {
                return new JSONArray(raw);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @NonNull
    private static JSONObject roleJson(@NonNull CachedRoleEntity role) {
        JSONObject obj = new JSONObject();
        put(obj, "role", role.role);
        put(obj, "name", role.name);
        put(obj, "description", role.description);
        return obj;
    }

    @NonNull
    private static String formatIso(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date(millis));
    }

    @NonNull
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void put(@NonNull JSONObject obj, @NonNull String key, Object value) {
        try {
            obj.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
