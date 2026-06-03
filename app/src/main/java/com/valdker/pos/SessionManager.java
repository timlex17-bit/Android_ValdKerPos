package com.valdker.pos;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;

import java.util.HashSet;
import java.util.Locale;
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
    private static final String KEY_SHIFT_STATUS = "shift_status";
    private static final String KEY_OPENING_CASH = "opening_cash";
    private static final String KEY_SHIFT_LOCAL_ID = "shift_local_id";

    private static final String KEY_SHOP_CODE = "shop_code";
    private static final String KEY_SHOP_ID = "shop_id";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_SHOP_NAME = "shop_name";
    private static final String KEY_SHOP_ADDRESS = "shop_address";
    private static final String KEY_SHOP_LOGO = "shop_logo";
    private static final String KEY_SHOP_BUSINESS_TYPE = "shop_business_type";
    private static final String KEY_IS_SUPERUSER = "is_superuser";
    private static final String KEY_IS_PLATFORM_ADMIN = "is_platform_admin";
    private static final String KEY_IS_SHOP_OWNER = "is_shop_owner";
    private static final String KEY_IS_SHOP_MANAGER = "is_shop_manager";
    private static final String KEY_IS_SHOP_CASHIER = "is_shop_cashier";
    private static final String KEY_BUSINESS_TYPE = "business_type";
    private static final String KEY_FEATURES_JSON = "features_json";

    private static final String KEY_TOKEN = "token";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLE = "role";
    private static final String KEY_PERMS = "perms";
    private static final String KEY_MENU_PERMISSIONS_JSON = "menu_permissions_json";

    private final SharedPreferences prefs;

    public SessionManager(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveUserProfile(
            @Nullable String token,
            @Nullable String username,
            @Nullable String fullName,
            @Nullable String role,
            int shopId,
            @Nullable String shopCode,
            @Nullable String shopName,
            @Nullable String shopAddress,
            @Nullable String shopLogo,
            boolean isSuperuser,
            boolean isPlatformAdmin,
            boolean isShopOwner,
            boolean isShopManager,
            boolean isShopCashier,
            @Nullable String businessType,
            @Nullable JSONObject features,
            @Nullable JSONArray permissions,
            @Nullable JSONObject menuPermissions
    ) {
        if (token == null) token = "";
        if (username == null) username = "";
        if (fullName == null) fullName = "";
        if (role == null) role = "";
        if (shopCode == null) shopCode = "";
        if (shopName == null) shopName = "";
        if (shopAddress == null) shopAddress = "";
        if (shopLogo == null) shopLogo = "";
        if (businessType == null) businessType = "retail";
        businessType = normalizeBusinessType(businessType);

        Set<String> permSet = new HashSet<>();
        if (permissions != null) {
            for (int i = 0; i < permissions.length(); i++) {
                String p = permissions.optString(i, "").trim();
                if (!p.isEmpty()) permSet.add(p);
            }
        }

        String featuresJson = features != null ? features.toString() : "{}";
        String menuPermissionsJson = menuPermissions != null ? menuPermissions.toString() : "{}";

        prefs.edit()
                .putString(KEY_TOKEN, token.trim())
                .putString(KEY_USERNAME, username.trim())
                .putString(KEY_FULL_NAME, fullName.trim())
                .putString(KEY_ROLE, role.trim())
                .putInt(KEY_SHOP_ID, shopId)
                .putString(KEY_SHOP_CODE, shopCode.trim())
                .putString(KEY_SHOP_NAME, shopName.trim())
                .putString(KEY_SHOP_ADDRESS, shopAddress.trim())
                .putString(KEY_SHOP_LOGO, shopLogo.trim())
                .putBoolean(KEY_IS_SUPERUSER, isSuperuser)
                .putBoolean(KEY_IS_PLATFORM_ADMIN, isPlatformAdmin)
                .putBoolean(KEY_IS_SHOP_OWNER, isShopOwner)
                .putBoolean(KEY_IS_SHOP_MANAGER, isShopManager)
                .putBoolean(KEY_IS_SHOP_CASHIER, isShopCashier)
                .putString(KEY_BUSINESS_TYPE, businessType)
                .putString(KEY_SHOP_BUSINESS_TYPE, businessType)
                .putString(KEY_FEATURES_JSON, featuresJson)
                .putStringSet(KEY_PERMS, permSet)
                .putString(KEY_MENU_PERMISSIONS_JSON, menuPermissionsJson)
                .apply();
    }

    public void applyCachedUserProfile(
            @Nullable String username,
            @Nullable String fullName,
            @Nullable String role,
            int shopId,
            @Nullable String shopCode,
            @Nullable String shopName,
            boolean isSuperuser,
            boolean isPlatformAdmin,
            boolean isShopOwner,
            boolean isShopManager,
            boolean isShopCashier,
            @Nullable String businessType,
            @Nullable JSONObject features,
            @Nullable JSONArray permissions,
            @Nullable JSONObject menuPermissions
    ) {
        saveUserProfile(
                getToken(),
                username != null && !username.trim().isEmpty() ? username : getUsername(),
                fullName,
                role,
                shopId,
                shopCode,
                shopName,
                getShopAddress(),
                getShopLogo(),
                isSuperuser,
                isPlatformAdmin,
                isShopOwner,
                isShopManager,
                isShopCashier,
                businessType,
                features,
                permissions,
                menuPermissions
        );
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
        return prefs.getInt(KEY_SHOP_ID, 0);
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

    public void setShiftStatus(@Nullable String status) {
        prefs.edit().putString(KEY_SHIFT_STATUS, status != null ? status : "").apply();
    }

    @NonNull
    public String getShiftStatus() {
        String v = prefs.getString(KEY_SHIFT_STATUS, "");
        return v != null ? v : "";
    }

    public void saveOpenShift(int id, @Nullable String status, @Nullable String openingCash) {
        if (status == null || status.trim().isEmpty()) status = "OPEN";
        if (openingCash == null || openingCash.trim().isEmpty()) openingCash = "0.00";

        prefs.edit()
                .putBoolean(KEY_SHIFT_OPEN, true)
                .putInt(KEY_SHIFT_ID, id)
                .putString(KEY_SHIFT_STATUS, status.trim())
                .putString(KEY_OPENING_CASH, openingCash.trim())
                .commit();
    }

    public void setOpeningCash(@Nullable String cash) {
        if (cash == null) cash = "0.00";
        prefs.edit().putString(KEY_OPENING_CASH, cash).apply();
    }

    @NonNull
    public String getBusinessType() {
        String v = prefs.getString(KEY_SHOP_BUSINESS_TYPE, null);
        if (v == null || v.trim().isEmpty()) {
            v = prefs.getString(KEY_BUSINESS_TYPE, "retail");
        }
        if (v == null || v.trim().isEmpty()) return "retail";
        return normalizeBusinessType(v);
    }

    @NonNull
    public String getShopBusinessType() {
        return getBusinessType();
    }

    public void setShopBusinessType(@Nullable String businessType) {
        String normalized = normalizeBusinessType(businessType);
        prefs.edit()
                .putString(KEY_BUSINESS_TYPE, normalized)
                .putString(KEY_SHOP_BUSINESS_TYPE, normalized)
                .apply();
    }

    @NonNull
    public JSONObject getShopFeatures() {
        String raw = prefs.getString(KEY_FEATURES_JSON, "{}");
        try {
            return new JSONObject(raw != null ? raw : "{}");
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public boolean getFeatureBoolean(@NonNull String key, boolean defaultValue) {
        JSONObject obj = getShopFeatures();
        return obj.optBoolean(key, defaultValue);
    }

    public boolean isRestaurant() {
        return "restaurant".equals(getBusinessType());
    }

    public boolean isRetail() {
        return "retail".equals(getBusinessType());
    }

    public boolean isWorkshop() {
        return "workshop".equals(getBusinessType());
    }

    public boolean useGridPosLayout() {
        if (isRestaurant()) {
            return getFeatureBoolean("use_grid_pos_layout", true);
        }
        return getFeatureBoolean("use_grid_pos_layout", false);
    }

    public boolean showProductImagesInPos() {
        if (isRestaurant()) {
            return getFeatureBoolean("show_product_images_in_pos", true);
        }
        return getFeatureBoolean("show_product_images_in_pos", false);
    }

    public boolean enableBarcodeScan() {
        return getFeatureBoolean("enable_barcode_scan", true);
    }

    public boolean enableDineIn() {
        if (isRestaurant()) {
            return getFeatureBoolean("enable_dine_in", true);
        }
        return false;
    }

    public boolean enableTakeaway() {
        if (isRestaurant()) {
            return getFeatureBoolean("enable_takeaway", true);
        }
        return false;
    }

    public boolean enableDelivery() {
        if (isRestaurant()) {
            return getFeatureBoolean("enable_delivery", true);
        }
        return false;
    }

    public boolean enableTableNumber() {
        if (isRestaurant()) {
            return getFeatureBoolean("enable_table_number", true);
        }
        return false;
    }

    public boolean enableSplitPayment() {
        return getFeatureBoolean("enable_split_payment", false);
    }

    @NonNull
    public String getOpeningCash() {
        String v = prefs.getString(KEY_OPENING_CASH, "0.00");
        return v != null ? v : "0.00";
    }

    @NonNull
    public String getShopCode() {
        String v = prefs.getString(KEY_SHOP_CODE, "");
        return v != null ? v : "";
    }

    @NonNull
    public String getShopLogo() {
        String v = prefs.getString(KEY_SHOP_LOGO, "");
        return v != null ? v : "";
    }

    public void clearShift() {
        prefs.edit()
                .putBoolean(KEY_SHIFT_OPEN, false)
                .putInt(KEY_SHIFT_ID, 0)
                .putString(KEY_SHIFT_STATUS, "")
                .putLong(KEY_SHIFT_LOCAL_ID, -1L)
                .putString(KEY_OPENING_CASH, "0.00")
                .commit();
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
                .putString(KEY_MENU_PERMISSIONS_JSON, "{}")
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

    public boolean isPlatformSuperuser() {
        return prefs.getBoolean(KEY_IS_SUPERUSER, false);
    }

    public boolean isPlatformAdmin() {
        return prefs.getBoolean(KEY_IS_PLATFORM_ADMIN, false);
    }

    public boolean isShopOwner() {
        return prefs.getBoolean(KEY_IS_SHOP_OWNER, false);
    }

    public boolean isShopManager() {
        return prefs.getBoolean(KEY_IS_SHOP_MANAGER, false);
    }

    public boolean isShopCashier() {
        return prefs.getBoolean(KEY_IS_SHOP_CASHIER, false);
    }

    @NonNull
    public String getFullName() {
        String v = prefs.getString(KEY_FULL_NAME, "");
        return v != null ? v : "";
    }

    @NonNull
    public String getShopName() {
        String v = prefs.getString(KEY_SHOP_NAME, "");
        return v != null ? v : "";
    }

    @NonNull
    public String getShopAddress() {
        String v = prefs.getString(KEY_SHOP_ADDRESS, "");
        return v != null ? v : "";
    }

    public void updateShopProfile(@Nullable String shopName,
                                  @Nullable String shopAddress,
                                  @Nullable String shopLogo) {
        if (shopName == null) shopName = "";
        if (shopAddress == null) shopAddress = "";
        if (shopLogo == null) shopLogo = "";

        prefs.edit()
                .putString(KEY_SHOP_NAME, shopName.trim())
                .putString(KEY_SHOP_ADDRESS, shopAddress.trim())
                .putString(KEY_SHOP_LOGO, shopLogo.trim())
                .apply();
    }

    public boolean isOwner() {
        return isPlatformSuperuser() || isPlatformAdmin() || isShopOwner();
    }

    public boolean canManageSettings() {
        if (hasMenuPermissions()) {
            return canAccessMenu("settings");
        }
        return hasPermission("settings.manage") || isOwner();
    }

    public boolean canViewReports() {
        if (hasMenuPermissions()) {
            return canAccessMenu("reports");
        }

        if (hasPermission("pos.view_reports") || hasPermission("reports.view")) {
            return true;
        }

        String role = getRole().trim().toLowerCase();
        return "owner".equals(role) || "manager".equals(role);
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

    @NonNull
    public JSONObject getMenuPermissions() {
        String raw = prefs.getString(KEY_MENU_PERMISSIONS_JSON, "{}");
        try {
            return new JSONObject(raw != null ? raw : "{}");
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public boolean hasMenuPermissions() {
        return getMenuPermissions().length() > 0;
    }

    public boolean canAccessMenu(@Nullable String menuKey) {
        if (menuKey == null) return false;

        String key = menuKey.trim();
        if (key.isEmpty()) return false;

        JSONObject menuPermissions = getMenuPermissions();
        if (menuPermissions.length() > 0) {
            if (!menuPermissions.has(key)) return false;
            Object value = menuPermissions.opt(key);
            if (value instanceof Boolean) return (Boolean) value;
            String rawValue = value != null ? String.valueOf(value).trim() : "";
            return "true".equalsIgnoreCase(rawValue) || "1".equals(rawValue);
        }

        return canAccessMenuByRole(key);
    }

    private boolean canAccessMenuByRole(@NonNull String menuKey) {
        String role = getRole().trim().toLowerCase(Locale.US);

        if (isOwner()
                || "owner".equals(role)
                || "admin".equals(role)
                || "superuser".equals(role)) {
            return isSupportedMenuKey(menuKey);
        }

        if (isShopManager() || "manager".equals(role)) {
            switch (menuKey) {
                case "pos":
                case "orders":
                case "customers":
                case "reports":
                case "stock_movements":
                case "inventory_counts":
                case "expenses":
                case "suppliers":
                case "purchases":
                case "products":
                case "categories":
                case "units":
                case "product_returns":
                case "stock_adjustments":
                    return true;
                default:
                    return false;
            }
        }

        if (isShopCashier() || "cashier".equals(role) || "kasir".equals(role)) {
            return "pos".equals(menuKey)
                    || "orders".equals(menuKey)
                    || "customers".equals(menuKey);
        }

        if ("inventory_staff".equals(role)) {
            switch (menuKey) {
                case "products":
                case "categories":
                case "units":
                case "warehouses":
                case "warehouse_stocks":
                case "stock_transfers":
                case "stock_movements":
                case "stock_adjustments":
                case "inventory_counts":
                    return true;
                default:
                    return false;
            }
        }

        if ("finance".equals(role)) {
            switch (menuKey) {
                case "reports":
                case "expenses":
                case "bank_accounts":
                case "bank_ledgers":
                case "orders":
                    return true;
                default:
                    return false;
            }
        }

        return false;
    }

    private boolean isSupportedMenuKey(@NonNull String menuKey) {
        switch (menuKey) {
            case "pos":
            case "orders":
            case "customers":
            case "reports":
            case "stock_movements":
            case "inventory_counts":
            case "settings":
            case "bank_accounts":
            case "expenses":
            case "suppliers":
            case "purchases":
            case "products":
            case "categories":
            case "units":
            case "product_returns":
            case "stock_adjustments":
            case "warehouses":
            case "warehouse_stocks":
            case "stock_transfers":
            case "bank_ledgers":
                return true;
            default:
                return false;
        }
    }

    public boolean isLoggedIn() {
        return !getToken().trim().isEmpty();
    }

    public void clear() {
        Log.w(TAG, "Clearing session");
        prefs.edit().clear().apply();
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
        editor.remove(KEY_PERMS);
        editor.remove(KEY_MENU_PERMISSIONS_JSON);
        editor.remove(KEY_SHOP_CODE);
        editor.remove(KEY_SHOP_ID);
        editor.remove(KEY_FULL_NAME);
        editor.remove(KEY_SHOP_NAME);
        editor.remove(KEY_SHOP_ADDRESS);
        editor.remove(KEY_SHOP_LOGO);
        editor.remove(KEY_IS_SUPERUSER);
        editor.remove(KEY_IS_PLATFORM_ADMIN);
        editor.remove(KEY_IS_SHOP_OWNER);
        editor.remove(KEY_IS_SHOP_MANAGER);
        editor.remove(KEY_IS_SHOP_CASHIER);
        editor.remove(KEY_BUSINESS_TYPE);
        editor.remove(KEY_SHOP_BUSINESS_TYPE);
        editor.remove(KEY_FEATURES_JSON);

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

    @NonNull
    private static String normalizeBusinessType(@Nullable String value) {
        if (value == null) return "retail";
        String clean = value.trim().toLowerCase(Locale.US);
        if (clean.isEmpty()) return "retail";
        if ("workshop".equals(clean) || "restaurant".equals(clean) || "retail".equals(clean)) {
            return clean;
        }
        return clean;
    }

    public void clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply();
    }

    public void logout() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
    }
}
