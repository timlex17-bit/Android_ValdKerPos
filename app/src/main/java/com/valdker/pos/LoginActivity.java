package com.valdker.pos;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.valdker.pos.utils.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.cart.CartManager;
import com.valdker.pos.repositories.AuthCacheRepository;
import com.valdker.pos.utils.ErrorHandler;
import com.valdker.pos.utils.LocaleHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyAppLocale(newBase));
    }

    private static final String TAG = "LOGIN";
    private static final String ENDPOINT_LOGIN = "api/auth/login/";
    private static final String ENDPOINT_AUTH_ME = "api/auth/me/";
    private static final Object LOGIN_TAG = "LoginActivityRequest";

    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 0;
    private static final float BACKOFF_MULT = 1.0f;
    private static final int MAX_SHOP_CODE_LENGTH = 24;
    private static final int MAX_USERNAME_LENGTH = 80;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private EditText etShopCode;
    private EditText etUsername;
    private EditText etPassword;
    private TextView btnTogglePwd;
    private Button btnLogin;
    private Button btnDemo;

    private boolean pwdVisible = false;
    private boolean isLoginInProgress = false;
    private SessionManager sm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        applySavedLanguageCompat();
        super.onCreate(savedInstanceState);
        protectLoginWindow();
        setContentView(R.layout.activity_login);

        sm = new SessionManager(this);

        bindViews();
        setupInputs();
        setupExistingSessionRedirect();
        setupActions();
    }

    private void protectLoginWindow() {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );
    }

    private void bindViews() {
        etShopCode = findViewById(R.id.etShopCode);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnTogglePwd = findViewById(R.id.btnTogglePwd);
        btnLogin = findViewById(R.id.btnLogin);
        btnDemo = findViewById(R.id.btnDemo);
    }

    private void applySavedLanguageCompat() {
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String languageCode = prefs.getString("app_language", "id");
        LocaleListCompat locales = LocaleListCompat.forLanguageTags(languageCode);
        AppCompatDelegate.setApplicationLocales(locales);
    }

    private void setupInputs() {
        if (etShopCode != null) {
            etShopCode.setFilters(new InputFilter[]{
                    new InputFilter.AllCaps(),
                    new InputFilter.LengthFilter(MAX_SHOP_CODE_LENGTH)
            });
            etShopCode.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }

        if (etUsername != null) {
            etUsername.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_USERNAME_LENGTH)});
            etUsername.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }

        if (etPassword != null) {
            etPassword.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_PASSWORD_LENGTH)});
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            etPassword.setSaveEnabled(false);
            etPassword.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    doLogin();
                    return true;
                }
                return false;
            });
        }
    }

    private void setupExistingSessionRedirect() {
        String token = sm.getToken();
        if (token != null && !token.trim().isEmpty()) {
            redirectBySavedSession();
        } else {
            sm.clearAuth();
        }
    }

    private void setupActions() {
        if (btnDemo != null) {
            if (BuildConfig.DEBUG) {
                btnDemo.setVisibility(View.VISIBLE);
                btnDemo.setOnClickListener(v -> {
                    if (etShopCode != null) etShopCode.setText("WFOUR");
                    if (etUsername != null) etUsername.setText("Rivaldo");
                    if (etPassword != null) etPassword.setText("admin123");
                });
            } else {
                btnDemo.setVisibility(View.GONE);
            }
        }

        if (btnTogglePwd != null) {
            btnTogglePwd.setOnClickListener(v -> togglePassword());
        }

        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> doLogin());
        }
    }

    private void redirectBySavedSession() {
        if (isCashierUser() && !sm.hasMenuPermissions()) {
            goToPOS();
        } else {
            goToDashboard();
        }
    }

    private boolean isCashierUser() {
        String role = safeLower(sm.getRole());
        return "cashier".equals(role) || "kasir".equals(role) || sm.isShopCashier();
    }

    private void doLogin() {
        if (etShopCode == null || etUsername == null || etPassword == null) return;
        if (isLoginInProgress) return;

        clearInputErrors();

        String shopCode = sanitizeShopCode(getText(etShopCode));
        String username = getText(etUsername);
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (shopCode.isEmpty() || username.isEmpty() || password.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_login_required_fields), Toast.LENGTH_SHORT).show();
            markRequiredFields(shopCode, username, password);
            return;
        }

        setLoading(true);

        try {
            JSONObject body = new JSONObject();
            body.put("shop_code", shopCode);
            body.put("username", username);
            body.put("password", password);

            String loginUrl = ApiConfig.url(sm, ENDPOINT_LOGIN);
            Log.i(TAG, "POST login endpoint shop_code=" + shopCode);

            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.POST,
                    loginUrl,
                    body,
                    response -> {
                        setLoading(false);
                        handleLoginSuccess(response, username, shopCode);
                    },
                    error -> {
                        setLoading(false);

                        int code = error.networkResponse != null ? error.networkResponse.statusCode : -1;
                        Log.e(TAG, "Login failed HTTP " + code, error);

                        if (code == 401 || code == 403) {
                            sm.clearAuth();
                        }

                        String msg;
                        if (code == 400) {
                            msg = getString(R.string.msg_login_required_fields_server);
                        } else if (code == 401) {
                            msg = getString(R.string.msg_login_invalid_shop_user_password);
                        } else if (code == 403) {
                            msg = getString(R.string.msg_user_inactive_or_forbidden_shop);
                        } else if (code == -1) {
                            msg = getString(R.string.msg_cannot_connect_server);
                        } else {
                            msg = getString(R.string.msg_login_failed_http, code);
                        }

                        if (code == -1) {
                            ErrorHandler.handleApiError(this, error);
                        } else {
                            ErrorHandler.handleApiError(this, msg);
                        }
                    }
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> h = new HashMap<>();
                    h.put("Accept", "application/json");
                    h.put("Content-Type", "application/json");
                    return h;
                }
            };

            req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
            req.setShouldCache(false);
            req.setTag(LOGIN_TAG);
            ApiClient.getInstance(this).add(req);

        } catch (Exception e) {
            setLoading(false);
            Log.e(TAG, "Login error", e);
            ErrorHandler.handleApiError(this, getString(R.string.msg_error_with_detail, e.getMessage()));
        }
    }

    private void handleLoginSuccess(@NonNull JSONObject response,
                                    @NonNull String fallbackUsername,
                                    @NonNull String fallbackShopCode) {
        String newToken = response.optString("token", "");
        if (newToken == null || newToken.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_token_missing_server_response), Toast.LENGTH_LONG).show();
            return;
        }

        fetchAuthMeAndPersist(newToken, response, fallbackUsername, fallbackShopCode);
    }

    private void fetchAuthMeAndPersist(@NonNull String token,
                                       @NonNull JSONObject loginResponse,
                                       @NonNull String fallbackUsername,
                                       @NonNull String fallbackShopCode) {
        String url = ApiConfig.url(sm, ENDPOINT_AUTH_ME);
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                authMe -> persistAuthProfile(authMe, token, fallbackUsername, fallbackShopCode),
                error -> {
                    Log.w(TAG, "auth/me failed. Falling back to login response.");
                    persistAuthProfile(loginResponse, token, fallbackUsername, fallbackShopCode);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                h.put("Authorization", "Token " + token.trim());
                return h;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);
        req.setTag(LOGIN_TAG);
        ApiClient.getInstance(this).add(req);
    }

    private void persistAuthProfile(@NonNull JSONObject response,
                                    @NonNull String authToken,
                                    @NonNull String fallbackUsername,
                                    @NonNull String fallbackShopCode) {
        try {
            JSONObject user = response.optJSONObject("user");
            JSONArray perms = response.optJSONArray("permissions");
            JSONArray effectiveModules = optJsonArrayFlexible(response, "effective_modules");
            JSONObject menuPermissions = optJsonObjectFlexible(user, "menu_permissions");
            if (menuPermissions == null) {
                menuPermissions = optJsonObjectFlexible(response, "menu_permissions");
            }

            JSONObject shop = response.optJSONObject("shop");

            String businessType = response.optString("shop_business_type", "");
            String plan = response.optString("plan", "");
            JSONObject features = new JSONObject();

            if (shop != null) {
                if (businessType == null || businessType.trim().isEmpty()) {
                    businessType = shop.optString("business_type", "");
                }
                if (plan == null || plan.trim().isEmpty()) {
                    plan = shop.optString("plan", "");
                }
                if (effectiveModules == null) {
                    effectiveModules = optJsonArrayFlexible(shop, "effective_modules");
                }

                JSONObject f = shop.optJSONObject("features");
                if (f != null) {
                    features = f;
                }
            }
            if ((businessType == null || businessType.trim().isEmpty()) && user != null) {
                businessType = user.optString("shop_business_type", "");
            }
            if ((plan == null || plan.trim().isEmpty()) && user != null) {
                plan = user.optString("plan", "");
            }
            if (effectiveModules == null && user != null) {
                effectiveModules = optJsonArrayFlexible(user, "effective_modules");
            }
            if (businessType == null || businessType.trim().isEmpty()) {
                businessType = "retail";
            }
            if (plan == null || plan.trim().isEmpty()) {
                plan = "basic";
            }

            Log.i(TAG, "Shop config loaded businessType=" + businessType + " plan=" + plan);

            String username = user != null
                    ? user.optString("username", fallbackUsername)
                    : fallbackUsername;

            String fullName = user != null
                    ? user.optString("full_name", "")
                    : "";

            String role = user != null
                    ? user.optString("role", "cashier")
                    : "cashier";

            int shopId = user != null
                    ? user.optInt("shop_id", 0)
                    : 0;
            if (shopId <= 0) {
                shopId = response.optInt("shop_id", 0);
            }
            if (shopId <= 0 && shop != null) {
                shopId = shop.optInt("id", 0);
            }
            if (shopId <= 0 && shop != null) {
                shopId = shop.optInt("shop_id", 0);
            }

            String shopCode = user != null
                    ? user.optString("shop_code", "")
                    : "";
            if (shopCode == null || shopCode.trim().isEmpty()) {
                shopCode = response.optString("shop_code", "");
            }
            if ((shopCode == null || shopCode.trim().isEmpty()) && shop != null) {
                shopCode = shop.optString("shop_code", "");
            }
            if ((shopCode == null || shopCode.trim().isEmpty()) && shop != null) {
                shopCode = shop.optString("code", "");
            }
            if (shopCode == null || shopCode.trim().isEmpty()) {
                shopCode = fallbackShopCode;
            }

            String shopName = user != null
                    ? user.optString("shop_name", "")
                    : "";
            if ((shopName == null || shopName.trim().isEmpty()) && shop != null) {
                shopName = shop.optString("name", "");
            }

            String shopAddress = shop != null
                    ? shop.optString("address", "")
                    : "";

            String shopLogo = "";

            if (shop != null) {
                shopLogo = shop.optString("logo_url", "");

                if (shopLogo == null || shopLogo.trim().isEmpty()) {
                    shopLogo = shop.optString("logo", "");
                }
            }

            boolean isSuperuser = user != null && user.optBoolean("is_superuser", false);
            boolean isPlatformAdmin = user != null && user.optBoolean("is_platform_admin", false);
            boolean isShopOwner = user != null && user.optBoolean("is_shop_owner", false);
            boolean isShopManager = user != null && user.optBoolean("is_shop_manager", false);
            boolean isShopCashier = user != null && user.optBoolean("is_shop_cashier", false);

            role = safeLower(role);

            Log.i(TAG, "Login success:"
                    + " role=" + role
                    + " shopId=" + shopId
                    + " shopCode=" + shopCode
                    + " isSuperuser=" + isSuperuser
                    + " isPlatformAdmin=" + isPlatformAdmin
                    + " isShopOwner=" + isShopOwner
                    + " isShopManager=" + isShopManager
                    + " isShopCashier=" + isShopCashier
                    + " permsCount=" + (perms != null ? perms.length() : 0)
                    + " menuPermsCount=" + (menuPermissions != null ? menuPermissions.length() : 0));

            sm.clearAuth();
            sm.saveUserProfile(
                    authToken,
                    username,
                    fullName,
                    role,
                    shopId,
                    shopCode,
                    shopName,
                    shopAddress,
                    shopLogo,
                    isSuperuser,
                    isPlatformAdmin,
                    isShopOwner,
                    isShopManager,
                    isShopCashier,
                    businessType,
                    plan,
                    effectiveModules,
                    features,
                    perms,
                    menuPermissions
            );
            new AuthCacheRepository(this).saveCurrentSessionFromLogin(response);

            CartManager cartManager = CartManager.getInstance(getApplicationContext());
            boolean cleared = cartManager.clearIfDifferentShop(shopId);
            if (cleared) {
                Log.w(TAG, "Cart dibersihkan karena shop login berubah.");
            }

            clearPasswordField();

            if (("cashier".equals(role) || "kasir".equals(role) || isShopCashier)
                    && !sm.hasMenuPermissions()) {
                goToPOS();
            } else {
                goToDashboard();
            }

        } catch (Exception e) {
            Log.e(TAG, "Response parse error", e);
            clearPasswordField();
            Toast.makeText(this, getString(R.string.msg_parse_error_with_detail, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void goToDashboard() {
        Intent i = new Intent(this, com.valdker.pos.ui.dashboard.HomeDashboardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToPOS() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void setLoading(boolean loading) {
        isLoginInProgress = loading;

        if (btnLogin != null) {
            btnLogin.setEnabled(!loading);
            btnLogin.setText(loading ? getString(R.string.msg_loading) : getString(R.string.login_btn));
        }

        if (btnDemo != null) {
            btnDemo.setEnabled(!loading);
        }

        if (etShopCode != null) etShopCode.setEnabled(!loading);
        if (etUsername != null) etUsername.setEnabled(!loading);
        if (etPassword != null) etPassword.setEnabled(!loading);
    }

    private void togglePassword() {
        if (etPassword == null || btnTogglePwd == null) return;

        pwdVisible = !pwdVisible;

        if (pwdVisible) {
            etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            btnTogglePwd.setText(getString(R.string.hide_password));
        } else {
            etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            btnTogglePwd.setText(getString(R.string.show_password));
        }

        if (etPassword.getText() != null) {
            etPassword.setSelection(etPassword.getText().length());
        }
    }

    @Nullable
    private JSONArray optJsonArrayFlexible(@Nullable JSONObject parent, @NonNull String key) {
        if (parent == null) return null;

        Object value = parent.opt(key);
        if (value instanceof JSONArray) {
            return (JSONArray) value;
        }

        if (value instanceof JSONObject) {
            JSONArray arr = new JSONArray();
            JSONObject obj = (JSONObject) value;
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String module = keys.next();
                Object enabled = obj.opt(module);
                if (enabled instanceof Boolean && (Boolean) enabled) {
                    arr.put(module);
                } else {
                    String raw = enabled != null ? String.valueOf(enabled).trim() : "";
                    if ("true".equalsIgnoreCase(raw) || "1".equals(raw)) {
                        arr.put(module);
                    }
                }
            }
            return arr;
        }

        if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.isEmpty()) return null;
            try {
                return new JSONArray(raw);
            } catch (Exception e) {
                Log.w(TAG, "Invalid JSON array for " + key + ": " + e.getMessage());
            }
        }

        return null;
    }

    @Nullable
    private JSONObject optJsonObjectFlexible(@Nullable JSONObject parent, @NonNull String key) {
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
            } catch (Exception e) {
                Log.w(TAG, "Invalid JSON object for " + key + ": " + e.getMessage());
            }
        }

        return null;
    }

    @NonNull
    private String getText(@Nullable EditText editText) {
        if (editText == null || editText.getText() == null) return "";
        return editText.getText().toString().trim();
    }

    @NonNull
    private String safeLower(@Nullable String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.US);
    }

    @NonNull
    private String sanitizeShopCode(@Nullable String value) {
        if (value == null) return "";
        return value.trim()
                .replaceAll("[^A-Za-z0-9_-]", "")
                .toUpperCase(Locale.US);
    }

    private void clearInputErrors() {
        if (etShopCode != null) etShopCode.setError(null);
        if (etUsername != null) etUsername.setError(null);
        if (etPassword != null) etPassword.setError(null);
    }

    private void markRequiredFields(
            @NonNull String shopCode,
            @NonNull String username,
            @NonNull String password
    ) {
        if (shopCode.isEmpty() && etShopCode != null) {
            etShopCode.setError(getString(R.string.msg_login_required_fields));
        }
        if (username.isEmpty() && etUsername != null) {
            etUsername.setError(getString(R.string.msg_login_required_fields));
        }
        if (password.trim().isEmpty() && etPassword != null) {
            etPassword.setError(getString(R.string.msg_login_required_fields));
        }
    }

    private void clearPasswordField() {
        if (etPassword != null) {
            etPassword.setText("");
        }
    }

    @Override
    protected void onDestroy() {
        try {
            ApiClient.getInstance(this).cancelAll(LOGIN_TAG);
        } catch (Exception ignored) {
        }
        clearPasswordField();
        super.onDestroy();
    }
}
