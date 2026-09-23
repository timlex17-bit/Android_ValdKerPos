package com.valdker.pos;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.network.BaseUrlRules;
import com.valdker.pos.network.BaseUrlStore;
import com.valdker.pos.ui.common.SystemBars;
import com.valdker.pos.utils.Toast;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Aktivasi perangkat: kode toko, sekali seumur pemasangan.
 *
 * <p>Urutan yang dilihat pengguna baru adalah onboarding -> layar ini -> layar
 * masuk. Sesudah kodenya tersimpan, layar ini tidak pernah tampil lagi;
 * satu-satunya jalan kembali adalah tindakan "Ganti toko" di layar masuk.
 *
 * <p>Kenapa layar tersendiri, bukan satu kolom tambahan di layar masuk: kedua
 * hal itu dijawab orang yang berbeda pada waktu yang berbeda. Kode toko
 * dibacakan pemilik sekali saat tablet dipasang; nama pengguna dan sandi
 * diketik kasir setiap pagi. Menyatukan keduanya membuat yang setahun sekali
 * terlihat sama pentingnya dengan yang tiap hari.
 *
 * <p><b>Apa yang diperiksa di sini.</b> Kode toko ditanyakan ke
 * {@code POST /api/shops/activate/} - endpoint tanpa autentikasi yang hanya
 * menjawab "kode ini milik toko mana". Ia TIDAK memberi akses apa pun: tidak
 * ada token, tidak ada sesi, dan login sesudahnya tetap memvalidasi ulang kode
 * toko terhadap toko milik pengguna yang masuk. Lihat
 * {@code docs/api/SHOP_ACTIVATE_API.md} di repo backend.
 *
 * <p><b>Kalau server tidak bisa menjawab, kodenya tetap diterima.</b> Aturan
 * itu disengaja dan alasannya ada di {@link #handleActivationError}: server
 * yang belum dipasangi endpoint ini - atau yang belum dijalankan migrasinya -
 * tidak boleh membuat tablet baru mustahil dipasang, karena sampai sebelum
 * endpoint ini ada, layar inilah yang menerima kodenya tanpa bertanya ke mana
 * pun.
 */
public class ShopActivationActivity extends AppCompatActivity {

    private static final String TAG = "ACTIVATION";
    private static final String ENDPOINT_ACTIVATE = "api/shops/activate/";
    private static final Object ACTIVATE_TAG = "ShopActivationRequest";

    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 0;
    private static final float BACKOFF_MULT = 1.0f;

    private static final int MAX_SHOP_CODE_LENGTH = 32;
    private static final int MIN_SHOP_CODE_LENGTH = 3;

    private SessionManager sm;
    private EditText etShopCode;
    private TextInputLayout tilShopCode;
    private EditText etServerUrl;
    private MaterialButton btnActivate;
    private View layoutAdvanced;
    private ImageView imgAdvancedChevron;
    private boolean advancedExpanded = false;
    private boolean activationInProgress = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shop_activation);

        sm = new SessionManager(this);

        // Perangkat yang sudah terikat tidak punya urusan di sini. Ini bukan
        // sekadar kerapian: layar ini bisa tercapai lewat tombol kembali, dan
        // menampilkannya lagi akan mengesankan kodenya perlu diketik ulang.
        if (sm.hasActivatedShop()) {
            openLogin();
            return;
        }

        // Latar layar ini ungu sampai ke tepi atas jendela, jadi yang di
        // balik bilah status sudah berwarna benar; yang perlu diatur hanya
        // ruangnya. Lihat catatan di activity_login.xml.
        SystemBars.apply(this);
        SystemBars.padTop(findViewById(R.id.content));
        SystemBars.padBottom(findViewById(R.id.scroll));

        bindViews();
        setupInputs();
        setupActions();
    }

    private void bindViews() {
        etShopCode = findViewById(R.id.etShopCode);
        tilShopCode = findViewById(R.id.tilShopCode);
        etServerUrl = findViewById(R.id.etServerUrl);
        btnActivate = findViewById(R.id.btnActivate);
        layoutAdvanced = findViewById(R.id.layoutAdvanced);
        imgAdvancedChevron = findViewById(R.id.imgAdvancedChevron);

        if (etServerUrl != null) {
            etServerUrl.setText(ApiConfig.base(sm));
        }
    }

    private void setupInputs() {
        if (etShopCode == null) return;

        etShopCode.setFilters(new InputFilter[]{
                new InputFilter.AllCaps(),
                new InputFilter.LengthFilter(MAX_SHOP_CODE_LENGTH)
        });
        etShopCode.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                activate();
                return true;
            }
            return false;
        });
    }

    private void setupActions() {
        if (btnActivate != null) {
            btnActivate.setOnClickListener(v -> activate());
        }

        View toggle = findViewById(R.id.btnToggleAdvanced);
        if (toggle != null) {
            toggle.setOnClickListener(v -> setAdvancedExpanded(!advancedExpanded));
        }
        setAdvancedExpanded(false);
    }

    private void setAdvancedExpanded(boolean expanded) {
        advancedExpanded = expanded;
        if (layoutAdvanced != null) {
            layoutAdvanced.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (imgAdvancedChevron != null) {
            imgAdvancedChevron.setRotation(expanded ? 180f : 0f);
        }
    }

    private void activate() {
        if (etShopCode == null || activationInProgress) return;

        String code = etShopCode.getText() == null
                ? ""
                : etShopCode.getText().toString().trim().toUpperCase(Locale.US);

        if (code.length() < MIN_SHOP_CODE_LENGTH) {
            showFieldError(R.string.activation_invalid);
            return;
        }

        // Alamat server diterapkan SEBELUM kode ditanyakan. Kalau alamatnya
        // salah, kode toko apa pun akan terlihat salah, dan yang disalahkan
        // pengguna adalah kodenya - bukan alamatnya.
        if (!applyServerUrlIfChanged()) return;

        clearFieldError();
        verifyWithServer(code);
    }

    /**
     * Menanyakan kode toko ke server sebelum menyimpannya.
     *
     * <p>Dua hal yang membaik dibanding menerimanya begitu saja: salah ketik
     * tertahan di layar ini alih-alih muncul sebagai "gagal masuk" satu layar
     * kemudian, dan nama tokonya bisa langsung diperlihatkan di layar masuk -
     * hanya responslah yang tahu nama itu sebelum ada seorang pun login.
     */
    private void verifyWithServer(@NonNull String code) {
        JSONObject body = new JSONObject();
        try {
            body.put("shop_code", code);
            body.put("device_id", sm.getOrCreateDeviceId());
        } catch (Exception e) {
            Log.e(TAG, "Gagal menyusun payload aktivasi", e);
            acceptWithoutServer(code);
            return;
        }

        setLoading(true);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                ApiConfig.url(sm, ENDPOINT_ACTIVATE),
                body,
                response -> {
                    if (isGone()) return;
                    setLoading(false);
                    handleActivationSuccess(code, response);
                },
                error -> {
                    if (isGone()) return;
                    setLoading(false);
                    handleActivationError(code, error);
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
        req.setTag(ACTIVATE_TAG);
        ApiClient.getInstance(this).add(req);
    }

    /**
     * Kode yang dikenali server. Yang disimpan adalah kode dari RESPONS, bukan
     * yang diketik: server menormalkannya ke huruf kapital, dan sejak itu
     * bentuk yang dipegang perangkat sama persis dengan yang dipegang server.
     */
    private void handleActivationSuccess(@NonNull String typedCode, @NonNull JSONObject response) {
        String serverCode = response.optString("shop_code", "").trim();
        String shopName = response.optString("shop_name", "").trim();

        Log.i(TAG, "Aktivasi berhasil untuk " + (serverCode.isEmpty() ? typedCode : serverCode));
        sm.setActivatedShop(serverCode.isEmpty() ? typedCode : serverCode, shopName);

        // Jenis usaha sudah diketahui DI SINI, sebelum siapa pun masuk:
        // POST /api/shops/activate/ mengembalikannya (docs/api/SHOP_ACTIVATE_API.md).
        // Sebelumnya nilai itu dibuang dan layar masuk tidak punya cara
        // menyebut tipe toko, sehingga kasir baru tahu ia di kasir mana
        // setelah berhasil login. Menyimpannya di sini membuat lencana
        // terkunci di layar masuk menampilkan sesuatu yang benar-benar
        // berasal dari server, bukan tebakan.
        String businessType = response.optString("business_type", "").trim();
        if (!businessType.isEmpty()) {
            sm.setShopBusinessType(businessType);
        }

        openLogin();
    }

    /**
     * Menerjemahkan kegagalan aktivasi, dan memutuskan apakah kodenya tetap
     * diterima.
     *
     * <p>Pembedanya adalah field {@code code} pada badan respons - itulah
     * kontraknya, bukan kalimat di {@code detail} yang boleh berubah kapan
     * saja. Kalau {@code code} ada dan dikenal, server benar-benar menjawab
     * pertanyaannya ("kode itu tidak ada", "tokonya nonaktif"), dan jawaban
     * itu dihormati: pengguna tertahan di layar ini.
     *
     * <p>Kalau tidak ada {@code code} yang dikenal - tidak ada respons sama
     * sekali, halaman 404 bawaan Django karena rutenya belum dipasang, 500
     * karena migrasinya belum dijalankan - maka server bukan sedang menolak
     * kodenya, melainkan sedang tidak bisa ditanya. Menahan pengguna di sini
     * berarti tablet baru tidak bisa dipasang sama sekali pada server yang
     * belum diperbarui, padahal sebelum endpoint ini ada, layar inilah yang
     * menerima kode tanpa bertanya ke mana pun. Jadi perilaku lama itulah yang
     * dipakai sebagai lantai: kodenya diterima, dan login pertama tetap
     * memvalidasinya persis seperti sebelumnya.
     */
    private void handleActivationError(@NonNull String code, @Nullable VolleyError error) {
        int status = error != null && error.networkResponse != null
                ? error.networkResponse.statusCode : -1;
        String serverCode = readErrorCode(error);

        switch (serverCode) {
            case "not_found":
                showFieldError(R.string.activation_not_found);
                return;
            case "shop_inactive":
                showNotice(R.string.activation_shop_inactive);
                return;
            case "rate_limited":
                showNotice(R.string.activation_rate_limited);
                return;
            case "shop_code_required":
            case "device_id_required":
                // Aplikasi selalu mengirim keduanya, jadi ini cacat di sisi
                // sini - bukan kesalahan pengguna. Dicatat sebagai galat
                // supaya terlihat di log, tetapi yang dilihat pengguna tetap
                // kalimat yang bisa ditindaklanjuti.
                Log.e(TAG, "Server menolak payload aktivasi: " + serverCode);
                showFieldError(R.string.activation_invalid);
                return;
            default:
                Log.w(TAG, "Aktivasi tidak terjawab server (HTTP " + status
                        + ", code=" + serverCode + "); kode diterima tanpa verifikasi.");
                acceptWithoutServer(code);
        }
    }

    /**
     * Membaca field {@code code} dari badan respons galat.
     *
     * @return kodenya, atau string kosong kalau tidak ada respons, badannya
     *         bukan JSON, atau JSON-nya tidak punya field itu.
     */
    @NonNull
    private String readErrorCode(@Nullable VolleyError error) {
        if (error == null || error.networkResponse == null || error.networkResponse.data == null) {
            return "";
        }
        try {
            String raw = new String(error.networkResponse.data, StandardCharsets.UTF_8).trim();
            if (!raw.startsWith("{")) return "";
            return new JSONObject(raw).optString("code", "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** Perilaku sebelum endpoint aktivasi ada: terima kodenya, login yang membuktikan. */
    private void acceptWithoutServer(@NonNull String code) {
        sm.setActivatedShop(code, "");
        openLogin();
    }

    /**
     * Galat yang memang soal kodenya, ditempel pada kolomnya.
     *
     * <p>Lewat {@link TextInputLayout}, bukan {@code EditText.setError()}:
     * yang terakhir menggambar gelembung melayang yang - terlihat saat diuji -
     * justru menutupi tombol "Lanjut" di bawahnya, sehingga pengguna membaca
     * keluhannya tetapi kehilangan tombol untuk mencoba lagi.
     */
    private void showFieldError(@StringRes int messageRes) {
        if (tilShopCode != null) {
            tilShopCode.setError(getString(messageRes));
        } else if (etShopCode != null) {
            etShopCode.setError(getString(messageRes));
        }
        if (etShopCode != null) etShopCode.requestFocus();
    }

    private void clearFieldError() {
        if (tilShopCode != null) tilShopCode.setError(null);
        if (etShopCode != null) etShopCode.setError(null);
    }

    /**
     * Untuk galat yang bukan soal kodenya - toko nonaktif, terlalu sering
     * mencoba. Menempelkannya pada kolom kode akan menyuruh pengguna
     * membetulkan sesuatu yang sudah benar.
     *
     * <p>Popup, bukan toast. Dua alasan: toast hilang sendiri dalam beberapa
     * detik dan sesudahnya layar tidak menyisakan jejak apa pun tentang kenapa
     * tombol tadi seperti tidak melakukan apa-apa; dan {@link Toast} milik
     * aplikasi ini memang meredam pesan yang berulang - persis keadaan
     * "terlalu sering mencoba", yang justru pesannya paling perlu terbaca.
     */
    private void showNotice(@StringRes int messageRes) {
        if (isGone()) return;
        new MaterialAlertDialogBuilder(this)
                .setMessage(messageRes)
                .setPositiveButton(R.string.action_ok, (d, w) -> d.dismiss())
                .show();
    }

    private void setLoading(boolean loading) {
        activationInProgress = loading;

        if (btnActivate != null) {
            btnActivate.setEnabled(!loading);
            btnActivate.setText(loading
                    ? getString(R.string.msg_loading)
                    : getString(R.string.activation_continue));
        }
        if (etShopCode != null) etShopCode.setEnabled(!loading);
        if (etServerUrl != null) etServerUrl.setEnabled(!loading);
    }

    private boolean isGone() {
        return isFinishing() || isDestroyed();
    }

    private boolean applyServerUrlIfChanged() {
        if (etServerUrl == null) return true;

        String typed = etServerUrl.getText() != null
                ? etServerUrl.getText().toString().trim() : "";
        if (typed.isEmpty()) return true;
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
        Toast.makeText(this, getString(result.messageRes), Toast.LENGTH_SHORT).show();
        return true;
    }

    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        try {
            ApiClient.getInstance(this).cancelAll(ACTIVATE_TAG);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
