package com.example.valdker;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;
import com.example.valdker.cart.CartManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LOGIN";
    private static final String ENDPOINT_LOGIN = "api/auth/login/";

    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 0;
    private static final float BACKOFF_MULT = 1.0f;

    private EditText etShopCode;
    private EditText etUsername;
    private EditText etPassword;
    private TextView btnTogglePwd;
    private Button btnLogin;
    private Button btnDemo;

    private boolean pwdVisible = false;
    private SessionManager sm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sm = new SessionManager(this);

        bindViews();
        setupInputs();
        setupExistingSessionRedirect();
        setupActions();
    }

    private void bindViews() {
        etShopCode = findViewById(R.id.etShopCode);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnTogglePwd = findViewById(R.id.btnTogglePwd);
        btnLogin = findViewById(R.id.btnLogin);
        btnDemo = findViewById(R.id.btnDemo);
    }

    private void setupInputs() {
        if (etShopCode != null) {
            etShopCode.setFilters(new InputFilter[]{new InputFilter.AllCaps()});
            etShopCode.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
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
            btnDemo.setOnClickListener(v -> {
                if (etShopCode != null) etShopCode.setText("WFOUR");
                if (etUsername != null) etUsername.setText("Rivaldo");
                if (etPassword != null) etPassword.setText("admin123");
            });
        }

        if (btnTogglePwd != null) {
            btnTogglePwd.setOnClickListener(v -> togglePassword());
        }

        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> doLogin());
        }
    }

    private void redirectBySavedSession() {
        if (isCashierUser()) {
            goToPOS();
        } else {
            goToDashboard();
        }
    }

    private boolean isCashierUser() {
        String role = safeLower(sm.getRole());
        return "cashier".equals(role) || sm.isShopCashier();
    }

    private void doLogin() {
        if (etShopCode == null || etUsername == null || etPassword == null) return;

        String shopCode = getText(etShopCode).toUpperCase(Locale.US);
        String username = getText(etUsername);
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (shopCode.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Shop code, username, and password are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        try {
            JSONObject body = new JSONObject();
            body.put("shop_code", shopCode);
            body.put("username", username);
            body.put("password", password);

            String loginUrl = ApiConfig.url(sm, ENDPOINT_LOGIN);
            Log.i(TAG, "POST " + loginUrl + " shop_code=" + shopCode + " username=" + username);
            Log.i(TAG, "Base URL = " + sm.getBaseUrl());

            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.POST,
                    loginUrl,
                    body,
                    response -> {
                        setLoading(false);
                        handleLoginSuccess(response, username);
                    },
                    error -> {
                        setLoading(false);

                        int code = error.networkResponse != null ? error.networkResponse.statusCode : -1;
                        String serverBody = extractErrorBody(error.networkResponse);

                        Log.e(TAG, "Login failed HTTP " + code + " body=" + serverBody, error);

                        if (code == 401 || code == 403) {
                            sm.clearAuth();
                        }

                        String msg;
                        if (code == 400) {
                            msg = "Shop code, username, and password must be filled.";
                        } else if (code == 401) {
                            msg = "Shop code, username, or password is invalid.";
                        } else if (code == 403) {
                            msg = "User is inactive or not allowed to access this shop.";
                        } else if (code == -1) {
                            msg = "Cannot connect to server. Check base URL or network.";
                        } else {
                            msg = "Login failed (HTTP " + code + ")";
                        }

                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
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
            ApiClient.getInstance(this).add(req);

        } catch (Exception e) {
            setLoading(false);
            Log.e(TAG, "Login error", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleLoginSuccess(@NonNull JSONObject response, @NonNull String fallbackUsername) {
        try {
            String newToken = response.optString("token", "");
            JSONObject user = response.optJSONObject("user");
            JSONArray perms = response.optJSONArray("permissions");

            JSONObject shop = response.optJSONObject("shop");

            String businessType = "retail";
            JSONObject features = new JSONObject();

            if (shop != null) {
                businessType = shop.optString("business_type", "retail");

                JSONObject f = shop.optJSONObject("features");
                if (f != null) {
                    features = f;
                }
            }

            Log.i(TAG, "Shop config:"
                    + " businessType=" + businessType
                    + " features=" + features.toString());

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

            String shopCode = user != null
                    ? user.optString("shop_code", "")
                    : "";

            String shopName = user != null
                    ? user.optString("shop_name", "")
                    : "";

            String shopAddress = shop != null
                    ? shop.optString("address", "")
                    : "";

            Log.i(TAG, "FULL SHOP JSON = " + (shop != null ? shop.toString() : "null"));
            Log.i(TAG, "SHOP ADDRESS FROM API = " + shopAddress);

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
                    + " username=" + username
                    + " role=" + role
                    + " shopId=" + shopId
                    + " shopCode=" + shopCode
                    + " shopName=" + shopName
                    + " isSuperuser=" + isSuperuser
                    + " isPlatformAdmin=" + isPlatformAdmin
                    + " isShopOwner=" + isShopOwner
                    + " isShopManager=" + isShopManager
                    + " isShopCashier=" + isShopCashier
                    + " permsCount=" + (perms != null ? perms.length() : 0));

            if (newToken == null || newToken.trim().isEmpty()) {
                Toast.makeText(this, "Token is missing from server response.", Toast.LENGTH_LONG).show();
                return;
            }

            sm.clearAuth();
            sm.saveUserProfile(
                    newToken,
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
                    features,
                    perms
            );

            CartManager cartManager = CartManager.getInstance(getApplicationContext());
            boolean cleared = cartManager.clearIfDifferentShop(shopId);
            if (cleared) {
                Log.w(TAG, "Cart dibersihkan karena shop login berubah.");
            }

            if ("cashier".equals(role) || isShopCashier) {
                goToPOS();
            } else {
                goToDashboard();
            }

        } catch (Exception e) {
            Log.e(TAG, "Response parse error", e);
            Toast.makeText(this, "Parse error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void goToDashboard() {
        Intent i = new Intent(this, com.example.valdker.ui.dashboard.HomeDashboardActivity.class);
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
        if (btnLogin != null) {
            btnLogin.setEnabled(!loading);
            btnLogin.setText(loading ? "Loading..." : "Login");
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
            btnTogglePwd.setText("🙈");
        } else {
            etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            btnTogglePwd.setText("👁");
        }

        if (etPassword.getText() != null) {
            etPassword.setSelection(etPassword.getText().length());
        }
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
    private String extractErrorBody(@Nullable NetworkResponse nr) {
        if (nr == null || nr.data == null || nr.data.length == 0) return "";
        try {
            String s = new String(nr.data, StandardCharsets.UTF_8).trim();
            if (s.length() > 300) s = s.substring(0, 300) + "...";
            return s;
        } catch (Exception ignored) {
            return "";
        }
    }
}