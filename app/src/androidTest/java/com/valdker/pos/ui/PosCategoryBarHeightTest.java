package com.valdker.pos.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.valdker.pos.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Menjaga tinggi bilah kategori kasir restoran.
 *
 * <p>Bilah ini hanya tampil untuk jenis usaha restoran - retail dan bengkel
 * menyembunyikannya di {@code MainActivity.applyBusinessTypeUi()} - dan
 * dipakai kasir puluhan kali per jam untuk berpindah kelompok menu. Ia pernah
 * dipendekkan sampai chipnya 34dp demi menyisakan ruang bagi daftar menu di
 * atasnya, dan sasaran ketuk sekecil itu membuat ketukan meleset menjadi hal
 * biasa. Uji ini mengunci dua hal yang mudah hilang lagi tanpa disadari:
 *
 * <ol>
 *   <li>Chipnya tidak pernah lebih pendek daripada 40dp, tinggi chip
 *       Material yang lazim untuk chip berisi ikon dan teks. Angka ini
 *       adalah LANTAI, bukan sasaran: pemilik toko sempat mencoba 48dp dan
 *       menilainya terlalu tinggi karena memakan daftar menu di atasnya,
 *       jadi yang dijaga di sini hanyalah bahwa ia tidak pernah kembali
 *       sependek 34dp - ukuran yang membuat ketukan sering meleset.</li>
 *   <li>Tinggi bilahnya tetap sama dengan tinggi chip ditambah dua kali jarak
 *       dalam RecyclerView-nya. Ketiga angka itu ada di tiga baris dimen yang
 *       terpisah, jadi mengubah satu saja - yang wajar dilakukan orang yang
 *       hanya ingin "menaikkan sedikit" - meninggalkan celah kosong di dalam
 *       kartunya atau justru memotong chipnya.</li>
 * </ol>
 *
 * <p>Keduanya diperiksa pada DUA kelompok sumber daya sekaligus: ponsel
 * (values/) dan layar lebar (values-sw600dp/). Perangkat penguji hanya salah
 * satunya, jadi tanpa {@code createConfigurationContext} separuh angkanya
 * tidak pernah tersentuh uji - dan persis separuh itulah yang pernah luput
 * ketika hanya nilai tablet yang dinaikkan sementara pemilik toko memakai
 * ponsel.
 */
@RunWith(AndroidJUnit4.class)
public class PosCategoryBarHeightTest {

    /**
     * Lantai tinggi chip. Bukan sasaran ketuk 48dp yang disarankan Material -
     * itu dinilai terlalu tinggi untuk bilah yang duduk tepat di bawah daftar
     * menu - melainkan tinggi chip Material yang lazim untuk chip berisi ikon
     * dan teks.
     */
    private static final int MIN_CHIP_DP = 40;

    /** smallestScreenWidthDp yang memilih values/ (ponsel) dan values-sw600dp/. */
    private static final int PHONE_SW_DP = 360;
    private static final int TABLET_SW_DP = 600;

    @Test
    public void categoryChipKeepsItsFloorOnPhones() {
        assertChipKeepsItsFloor(PHONE_SW_DP);
    }

    @Test
    public void categoryChipKeepsItsFloorOnWideScreens() {
        assertChipKeepsItsFloor(TABLET_SW_DP);
    }

    @Test
    public void categoryBarHeightMatchesChipPlusPaddingOnPhones() {
        assertBarMatchesChip(PHONE_SW_DP);
    }

    @Test
    public void categoryBarHeightMatchesChipPlusPaddingOnWideScreens() {
        assertBarMatchesChip(TABLET_SW_DP);
    }

    private void assertChipKeepsItsFloor(int smallestWidthDp) {
        try (ActivityScenario<TestHostActivity> scenario =
                     ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                Context ctx = configFor(activity, smallestWidthDp);
                int chip = ctx.getResources().getDimensionPixelSize(R.dimen.category_chip_height);
                int minimum = dpToPx(ctx, MIN_CHIP_DP);

                if (chip < minimum) {
                    throw new AssertionError("Chip kategori pada sw" + smallestWidthDp
                            + "dp setinggi " + chip + "px, di bawah lantai "
                            + minimum + "px (" + MIN_CHIP_DP + "dp).");
                }

                // Diukur sungguhan, bukan hanya angka dimennya: chip yang
                // tingginya hanya minHeight tetap bisa dipendekkan oleh
                // induknya, dan yang ditekan kasir adalah hasil pengukuran.
                LayoutInflater inflater = LayoutInflater.from(ctx).cloneInContext(ctx);
                FrameLayout parent = activity.container();
                View chipView = inflater.inflate(R.layout.item_category_chip, parent, false);
                chipView.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

                if (chipView.getMeasuredHeight() < minimum) {
                    throw new AssertionError("Chip kategori pada sw" + smallestWidthDp
                            + "dp terukur " + chipView.getMeasuredHeight()
                            + "px, di bawah lantai " + minimum + "px.");
                }
            });
        }
    }

    private void assertBarMatchesChip(int smallestWidthDp) {
        try (ActivityScenario<TestHostActivity> scenario =
                     ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                Context ctx = configFor(activity, smallestWidthDp);
                int chip = ctx.getResources().getDimensionPixelSize(R.dimen.category_chip_height);
                int padding = ctx.getResources().getDimensionPixelSize(R.dimen.category_bar_padding);
                int bar = ctx.getResources().getDimensionPixelSize(R.dimen.category_bar_min_height);

                int expected = chip + 2 * padding;
                if (bar != expected) {
                    throw new AssertionError("Bilah kategori pada sw" + smallestWidthDp
                            + "dp setinggi " + bar + "px, sementara chip + dua kali jarak dalam ="
                            + " " + expected + "px (" + chip + " + 2x" + padding + ")."
                            + " Ketiga dimen itu harus berubah bersama-sama.");
                }
            });
        }
    }

    /**
     * Konteks dengan lebar layar terkecil yang dipaksa, supaya kelompok
     * sumber daya yang dipilih adalah values/ atau values-sw600dp/ tanpa
     * bergantung pada perangkat yang kebetulan dipakai menjalankan uji.
     */
    private Context configFor(TestHostActivity activity, int smallestWidthDp) {
        Configuration config = new Configuration(activity.getResources().getConfiguration());
        config.smallestScreenWidthDp = smallestWidthDp;
        config.screenWidthDp = smallestWidthDp;
        Context ctx = activity.createConfigurationContext(config);
        ctx.setTheme(R.style.Theme_ValdKer);
        return ctx;
    }

    private int dpToPx(Context ctx, int dp) {
        DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics));
    }
}
