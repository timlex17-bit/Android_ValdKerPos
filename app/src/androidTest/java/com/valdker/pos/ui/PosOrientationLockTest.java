package com.valdker.pos.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.valdker.pos.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Menjaga agar ponsel terkunci tegak dan tablet tetap bebas berputar.
 *
 * <p>Aturannya dipasang sekali di {@code ValdkerApp} lewat
 * {@code ActivityLifecycleCallbacks}, sehingga berlaku untuk seluruh Activity
 * tanpa satu baris pun yang perlu disalin ke masing-masing. Uji ini memeriksa
 * kedua sisinya:
 *
 * <ol>
 *   <li>Activity yang benar-benar diluncurkan pada perangkat ini memang
 *       menerima orientasi yang sesuai dengan ukuran layarnya - ini yang
 *       membuktikan pemasangannya bekerja, bukan sekadar kodenya ada;
 *   <li>{@code pos_allow_rotation} menjawab dengan benar di setiap ukuran
 *       layar, dari ponsel 320dp sampai tablet 1000dp. Bagian ini berjalan di
 *       perangkat mana pun, jadi aturan tabletnya tetap terperiksa meski
 *       rangkaian uji hanya pernah dijalankan di emulator ponsel.
 * </ol>
 *
 * <p>Batas pemisahnya sw600dp - sama dengan batas yang dipakai seluruh tata
 * letak kasir. Begitu sebuah perangkat membaca {@code layout-sw600dp/}, ia
 * sudah punya susunan dua kolom yang memang dirancang untuk dilihat mendatar,
 * jadi menguncinya tegak justru membuang rancangan itu.
 */
@RunWith(AndroidJUnit4.class)
public class PosOrientationLockTest {

    /** lebarTerkecilDp, bolehBerputar */
    private static final Object[][] EXPECTED_BY_SMALLEST_WIDTH = {
            {320, false},   // ponsel kecil
            {360, false},   // ponsel acuan
            {411, false},   // ponsel besar
            {599, false},   // tepat di bawah batas
            {600, true},    // tablet 7 inci, dan ponsel lipat yang dibuka
            {800, true},    // tablet 10 inci
            {1000, true},   // tablet 13 inci
    };

    @Test
    public void everyActivityIsLockedToPortraitOnPhones() {
        try (ActivityScenario<TestHostActivity> scenario =
                     ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                boolean allowsRotation =
                        activity.getResources().getBoolean(R.bool.pos_allow_rotation);

                if (allowsRotation) {
                    // Tablet: TIDAK ada yang disetel sama sekali. Menyetel
                    // orientasi - bahkan ke UNSPECIFIED - dapat memicu
                    // Activity dibangun ulang bila posisi perangkat saat itu
                    // berbeda, dan itu berarti tablet mendatar berkedip sekali
                    // setiap membuka layar.
                    assertEquals("Tablet tidak boleh dikunci orientasinya",
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                            activity.getRequestedOrientation());
                } else {
                    assertEquals("Ponsel harus terkunci tegak",
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                            activity.getRequestedOrientation());
                }
            });
        }
    }

    @Test
    public void rotationIsAllowedFromSixHundredDpUpwards() {
        List<String> problems = new ArrayList<>();
        try (ActivityScenario<TestHostActivity> scenario =
                     ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                for (Object[] row : EXPECTED_BY_SMALLEST_WIDTH) {
                    int smallestWidthDp = (Integer) row[0];
                    boolean expected = (Boolean) row[1];

                    Configuration config =
                            new Configuration(activity.getResources().getConfiguration());
                    config.smallestScreenWidthDp = smallestWidthDp;
                    config.screenWidthDp = smallestWidthDp;
                    Context scaled = activity.createConfigurationContext(config);

                    boolean actual = scaled.getResources().getBoolean(R.bool.pos_allow_rotation);
                    if (actual != expected) {
                        problems.add(String.format(Locale.US,
                                "sw%ddp: pos_allow_rotation=%b, seharusnya %b",
                                smallestWidthDp, actual, expected));
                    }
                }
            });
        }
        if (!problems.isEmpty()) {
            fail("Batas boleh-berputar tidak jatuh di sw600dp:\n  "
                    + String.join("\n  ", problems));
        }
    }
}
