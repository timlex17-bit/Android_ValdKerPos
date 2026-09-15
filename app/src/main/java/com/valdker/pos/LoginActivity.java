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
import android.widget.ImageView;
import android.widget.TextView;
import com.valdker.pos.ui.common.SystemBars;
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
import com.valdker.pos.network.BaseUrlRules;
import com.valdker.pos.network.BaseUrlStore;
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
    private static final int MAX_USERNAME_LENGTH = 80;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private View groupActiveShop;
    private TextView tvActiveShop;
    private View btnChangeShop;
    private EditText etUsername;
    private EditText etPassword;
    private Button btnLogin;
    private Button btnDemo;

    private EditText etServerUrl;
    private View layoutAdvanced;
    private View btnToggleAdvanced;
    private ImageView imgAdvancedChevron;
    private TextView tvServerOrigin;
    private boolean isLoginInProgress = false;
    private SessionManager sm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        applySavedLanguageCompat();
        super.onCreate(savedInstanceState);

        sm = new SessionManager(this);

        // Perangkat yang belum terikat pada sebuah toko tidak bisa masuk ke
        // mana pun, jadi layar ini tidak perlu tampil dulu. Pemeriksaan
        // ditaruh DI SINI, bukan di layar onboarding, supaya seluruh pintu
        // masuk ke layar login - keluar, sesi kedaluwarsa, ganti toko -
        // melewati aturan yang sama tanpa ada satu pun yang perlu diubah.
        if (!sm.hasActivatedShop()) {
            startActivity(new android.content.Intent(this, ShopActivationActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        protectLoginWindow();
        setContentView(R.layout.activity_login);
        setupSystemBars();

        bindViews();
        setupAdvanced();
        setupInputs();
        setupExistingSessionRedirect();
        setupActions();
    }

    /**
     * Layar masuk memakai bilah status ungu yang sama dengan seluruh aplikasi.
     * Sebelumnya ia tidak menyiapkan bilah sistem sama sekali, jadi warnanya
     * bergantung pada tema - yang sejak targetSdk 36 diabaikan Android 15+ -
     * dan strip di belakang bilah status menampilkan latar terang layar ini
     * dengan ikon yang warnanya ditentukan layar sebelumnya.
     */
    private void setupSystemBars() {
        SystemBars.apply(this);
        SystemBars.fitStatusScrim(findViewById(R.id.statusBarScrim));
        SystemBars.padBottom(findViewById(R.id.scroll));
    }

    private void protectLoginWindow() {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );
    }

    private void bindViews() {
        groupActiveShop = findViewById(R.id.groupActiveShop);
        tvActiveShop = findViewById(R.id.tvActiveShop);
        btnChangeShop = findViewById(R.id.btnChangeShop);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnDemo = findViewById(R.id.btnDemo);

        etServerUrl = findViewById(R.id.etServerUrl);
        layoutAdvanced = findViewById(R.id.layoutAdvanced);
        btnToggleAdvanced = findViewById(R.id.btnToggleAdvanced);
        imgAdvancedChevron = findViewById(R.id.imgAdvancedChevron);
        tvServerOrigin = findViewById(R.id.tvServerOrigin);
    }

    private void applySavedLanguageCompat() {
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String languageCode = prefs.getString("app_language", "id");
        LocaleListCompat locales = LocaleListCompat.forLanguageTags(languageCode);
        AppCompatDelegate.setApplicationLocales(locales);
    }

    private void setupInputs() {
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

    /**
     * Bagian "Lanjutan" di layar Login.
     *
     * <p>Terlipat secara bawaan: kasir harian tidak pernah perlu melihat
     * alamat server, dan kolom yang salah diisi di sini mengunci aplikasi.
     * Yang membukanya adalah ketukan sengaja - atau kegagalan koneksi, karena
     * saat itulah alamat server menjadi tersangka utama.
     */
    private void setupAdvanced() {
        if (btnToggleAdvanced != null) {
            btnToggleAdvanced.setOnClickListener(v -> setAdvancedExpanded(
                    layoutAdvanced == null || layoutAdvanced.getVisibility() != View.VISIBLE));
        }

        if (etServerUrl != null) {
            etServerUrl.setText(ApiConfig.base(sm));
            etServerUrl.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    v.clearFocus();
                    return true;
                }
                return false;
            });
        }

        refreshServerOrigin();
        setAdvancedExpanded(false);
    }

    private void setAdvancedExpanded(boolean expanded) {
        if (layoutAdvanced != null) {
            layoutAdvanced.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (imgAdvancedChevron != null) {
            imgAdvancedChevron.setImageResource(
                    expanded ? R.drawable.ic_arrow_up : R.drawable.ic_arrow_down);
        }
    }

    private void refreshServerOrigin() {
        if (tvServerOrigin == null) return;
        tvServerOrigin.setText(getString(
                R.string.login_server_origin,
                ApiConfig.base(sm),
                ApiConfig.originLabel(sm)));
    }

    /**
     * Menyimpan alamat server kalau pengguna mengubahnya, tepat sebelum
     * percobaan login memakainya.
     *
     * @return false kalau alamatnya ditolak, sehingga login harus dibatalkan.
     */
    private boolean applyServerUrlIfChanged() {
        if (etServerUrl == null) return true;

        String typed = etServerUrl.getText() != null
                ? etServerUrl.getText().toString().trim() : "";
        if (typed.isEmpty()) return true;

        // Tidak ada perubahan berarti tidak perlu membuang cache dan sesi.
        if (BaseUrlRules.normalize(typed).equals(ApiConfig.base(sm))) return true;

        BaseUrlStore.Result result = BaseUrlStore.save(this, typed);
        if (!result.ok) {
            setAdvancedExpanded(true);
            etServerUrl.setError(getString(result.messageRes));
            etServerUrl.requestFocus();
            return false;
        }

        etServerUrl.setError(null);
        etServerUrl.setText(ApiConfig.base(sm));
        refreshServerOrigin();
        Toast.makeText(this, getString(result.messageRes), Toast.LENGTH_SHORT).show();
        return true;
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
                    if (etUsername != null) etUsername.setText("Rivaldo");
                    if (etPassword != null) etPassword.setText("admin123");
                });
            } else {
                btnDemo.setVisibility(View.GONE);
            }
        }

        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> doLogin());
        }

        if (btnChangeShop != null) {
            btnChangeShop.setOnClickListener(v -> confirmChangeShop());
        }

        applyActivationState();
    }

    /**
     * Menyesuaikan layar dengan status aktivasi perangkat.
     *
     * <p>Perangkat yang sudah terikat pada sebuah toko tidak lagi menampilkan
     * kolom kode toko sama sekali - kode itu tidak berubah sepanjang umur
     * perangkat, jadi memintanya setiap pagi hanya menambah satu kolom yang
     * bisa salah ketik. Sebagai gantinya toko aktifnya diperlihatkan, karena
     * kasir tetap berhak tahu dia sedang masuk ke toko mana.
     */
    private void applyActivationState() {
        boolean activated = sm.hasActivatedShop();

        if (groupActiveShop != null) {
            groupActiveShop.setVisibility(activated ? View.VISIBLE : View.GONE);
        }
        if (tvActiveShop != null && activated) {
            String name = sm.getActivatedShopName();
            String code = sm.getActivatedShopCode();
            tvActiveShop.setText(name.isEmpty() ? code : name + " · " + code);
        }
    }

    /**
     * Melepas ikatan perangkat dari tokonya.
     *
     * <p>Selalu lewat konfirmasi: sekali dilepas, kasir harus tahu kode toko
     * untuk bisa masuk lagi - dan kode itu justru hal yang tidak dihafal
     * siapa pun setelah perangkat dipakai berbulan-bulan.
     */
    private void confirmChangeShop() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.login_change_shop_title)
                .setMessage(R.string.login_change_shop_message)
                .setNegativeButton(R.string.action_cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_change, (d, w) -> {
                    sm.clearActivatedShop();
                    startActivity(new android.content.Intent(this, ShopActivationActivity.class)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                })
                .show();
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
        if (etUsername == null || etPassword == null) return;
        if (isLoginInProgress) return;

        clearInputErrors();

        // Kode toko tidak pernah diketik di layar ini: ia berasal dari
        // aktivasi perangkat. Layar ini bahkan tidak tampil sebelum aktivasi
        // selesai - lihat pemeriksaan di onCreate().
        String shopCode = sm.getActivatedShopCode();
        String username = getText(etUsername);
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (shopCode.isEmpty() || username.isEmpty() || password.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_login_required_fields), Toast.LENGTH_SHORT).show();
            markRequiredFields(username, password);
            return;
        }

        // Alamat server diterapkan sebelum request dibuat, supaya percobaan
        // login ini memakai alamat yang baru saja diketik - bukan yang lama.
        if (!applyServerUrlIfChanged()) return;

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
                            // Tidak ada respons sama sekali: alamat server
                            // adalah tersangka pertama, jadi kolomnya dibuka
                            // alih-alih dibiarkan tersembunyi.
                            setAdvancedExpanded(true);
                            refreshServerOrigin();
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
            String taxPercent = "";

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

                JSONObject posSettings = shop.optJSONObject("pos_settings");
                if (posSettings != null) {
                    taxPercent = posSettings.optString("tax_percent", "");
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
            sm.setTaxPercent(taxPercent);

            // Aktivasi sudah menyimpan kode ini sebelum layar masuk tampil;
            // yang ditulis ulang di sini adalah NAMA tokonya. Aktivasi tidak
            // selalu mendapatkannya - pada server yang belum punya endpoint
            // aktivasi, ShopActivationActivity menerima kodenya tanpa nama -
            // dan login inilah kesempatan berikutnya untuk melengkapinya,
            // supaya layar masuk memperlihatkan toko mana, bukan sekadar
            // kodenya. Kode dari respons juga menang atas yang tersimpan:
            // keduanya pasti sama, karena login menolak kode yang bukan milik
            // pengguna ini.
            sm.setActivatedShop(shopCode, shopName);
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

        if (etUsername != null) etUsername.setEnabled(!loading);
        if (etPassword != null) etPassword.setEnabled(!loading);
    }

    /*
     * togglePassword() dihapus. Sebelumnya ada TextView "Tampilkan/Sembunyikan"
     * di sebelah kolom sandi beserta penanda pwdVisible dan dua string; kini
     * TextInputLayout menyediakan ikon mata bawaan lewat
     * app:endIconMode="password_toggle" - ikon yang sama dengan yang dikenal
     * pengguna dari aplikasi lain, sudah punya contentDescription, dan tidak
     * perlu dijaga sinkron dengan keadaan apa pun.
     */


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

    private void clearInputErrors() {
        if (etUsername != null) etUsername.setError(null);
        if (etPassword != null) etPassword.setError(null);
    }

    private void markRequiredFields(
            @NonNull String username,
            @NonNull String password
    ) {
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
