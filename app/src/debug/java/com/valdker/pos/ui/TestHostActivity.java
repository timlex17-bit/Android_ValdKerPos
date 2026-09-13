package com.valdker.pos.ui;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.valdker.pos.R;

/**
 * Activity kosong bertema aplikasi, hanya untuk pengujian.
 *
 * <p>Tinggal di source set debug, bukan androidTest, karena APK pengujian
 * memakai paket com.valdker.pos.test sementara instrumentasi berjalan di
 * proses com.valdker.pos; Activity yang dideklarasikan di manifes androidTest
 * tidak bisa diluncurkan dari sana. Dengan berada di src/debug ia ikut APK
 * aplikasi debug dan tidak pernah masuk build rilis.
 *
 * <p>Perlu Activity sungguhan - bukan sekadar ContextThemeWrapper - karena
 * AppCompatActivity memasang AppCompatViewInflater pada LayoutInflater-nya.
 * Inflater itulah yang memetakan {@code <Switch>} ke SwitchCompat dan memberi
 * komponen Material konteks bertema yang mereka butuhkan. Menginflate lewat
 * LayoutInflater polos gagal pada layout yang sebenarnya baik-baik saja di
 * dalam aplikasi, jadi uji yang memakai inflater polos akan melaporkan
 * kegagalan palsu.
 */
public class TestHostActivity extends AppCompatActivity {

    private FrameLayout container;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_ValdKer);
        super.onCreate(savedInstanceState);
        container = new FrameLayout(this);
        setContentView(container);
    }

    public FrameLayout container() {
        return container;
    }
}
