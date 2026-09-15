package com.valdker.pos;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.network.BaseUrlRules;
import com.valdker.pos.network.BaseUrlStore;
import com.valdker.pos.ui.common.SystemBars;
import com.valdker.pos.utils.Toast;

import java.util.Locale;

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
 * <p><b>Apa yang diperiksa di sini, dan apa yang tidak.</b> Layar ini hanya
 * memeriksa bentuk kodenya lalu menyimpannya; yang membuktikan kode itu benar
 * ada di server adalah login pertama, karena endpoint login memang sudah
 * memvalidasi shop_code. Jadi kode yang salah ketik tidak tertahan di sini
 * melainkan pada percobaan masuk berikutnya - dan dari sana pengguna bisa
 * kembali lewat "Ganti toko". Begitu backend menyediakan endpoint aktivasi
 * tersendiri, pemeriksaannya dipasang di {@link #activate()} dan sisa alurnya
 * tidak berubah.
 */
public class ShopActivationActivity extends AppCompatActivity {

    private static final int MAX_SHOP_CODE_LENGTH = 32;
    private static final int MIN_SHOP_CODE_LENGTH = 3;

    private SessionManager sm;
    private EditText etShopCode;
    private EditText etServerUrl;
    private View layoutAdvanced;
    private ImageView imgAdvancedChevron;
    private boolean advancedExpanded = false;

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

        SystemBars.apply(this);
        SystemBars.fitStatusScrim(findViewById(R.id.statusBarScrim));
        SystemBars.padBottom(findViewById(R.id.scroll));

        bindViews();
        setupInputs();
        setupActions();
    }

    private void bindViews() {
        etShopCode = findViewById(R.id.etShopCode);
        etServerUrl = findViewById(R.id.etServerUrl);
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
        MaterialButton btnActivate = findViewById(R.id.btnActivate);
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
        if (etShopCode == null) return;

        String code = etShopCode.getText() == null
                ? ""
                : etShopCode.getText().toString().trim().toUpperCase(Locale.US);

        if (code.length() < MIN_SHOP_CODE_LENGTH) {
            etShopCode.setError(getString(R.string.activation_invalid));
            etShopCode.requestFocus();
            return;
        }

        // Alamat server diterapkan SEBELUM kode disimpan. Kalau alamatnya
        // salah, kode toko apa pun akan terlihat salah pada login nanti, dan
        // yang disalahkan pengguna adalah kodenya - bukan alamatnya.
        if (!applyServerUrlIfChanged()) return;

        etShopCode.setError(null);
        sm.setActivatedShop(code, "");
        openLogin();
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
}
