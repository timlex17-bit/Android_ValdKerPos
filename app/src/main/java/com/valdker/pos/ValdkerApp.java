package com.valdker.pos;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.valdker.pos.network.ApiConfig;

public class ValdkerApp extends Application {

    private static final String TAG = "BACKEND";

    @Override
    public void onCreate() {
        super.onCreate();

        // Dicatat sebelum request apa pun berjalan. Sebuah putaran pengujian
        // pernah berjalan penuh terhadap server yang salah tanpa disadari,
        // karena tujuan aplikasi tidak pernah disebut di mana pun kecuali di
        // dalam URL setiap request. Sekarang ia dinyatakan sekali, di awal.
        Log.i(TAG, "base URL aktif = " + ApiConfig.describe(new SessionManager(this)));

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String languageCode = prefs.getString("app_language", "tet");

        LocaleListCompat locales = LocaleListCompat.forLanguageTags(languageCode);
        AppCompatDelegate.setApplicationLocales(locales);
    }
}
