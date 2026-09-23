package com.valdker.pos.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.valdker.pos.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Menjaga lebar popup di seluruh aplikasi.
 *
 * <p>Pemilik toko mengeluhkan dialog yang terasa kurus, dan menunjuk satu
 * dialog yang terasa benar - "Select Customer & Vehicle" di kasir bengkel.
 * Dialog itu memang lain sendiri: ia memaksa lebarnya ke 94% layar lewat
 * {@code window.setLayout()}, sementara semua dialog lain memakai
 * {@code @dimen/dialog_min_width} yang bernilai 300dp TETAP - hanya 83% pada
 * ponsel 360dp, bahkan lebih sempit daripada bawaan AppCompat sendiri (95%).
 * Jadi token yang dulu ditambahkan demi tablet justru menyempitkan ponsel.
 *
 * <p>Sekarang aturannya satu, ada di tema, dan uji ini mengunci dua sisinya:
 *
 * <ol>
 *   <li>Di ponsel, lebar minimum dialog tidak pernah turun di bawah 90% lebar
 *       layar pada potret. Ini yang dirasakan pengguna.</li>
 *   <li>Di layar lebar, lebarnya BERHENTI - dinyatakan dalam dp, bukan
 *       persentase. Satu baris teks yang membentang 1200dp lebih sulit dibaca,
 *       bukan lebih mudah, jadi "lebih lebar" bukan selalu lebih baik.</li>
 * </ol>
 *
 * <p>Nilai dimennya diperiksa pada DUA kelompok sumber daya sekaligus lewat
 * {@code createConfigurationContext}, karena perangkat penguji hanya salah
 * satunya - tanpa itu separuh angkanya tidak pernah tersentuh uji.
 */
@RunWith(AndroidJUnit4.class)
public class DialogWidthTest {

    private static final int PHONE_SW_DP = 360;
    private static final int TABLET_SW_DP = 600;

    /** Lantai lebar dialog di ponsel, sebagai pecahan lebar layar. */
    private static final float MIN_PHONE_FRACTION = 0.90f;

    /** Lantai lebar dialog di layar lebar, dalam dp. */
    private static final int MIN_TABLET_DP = 520;

    // ------------------------------------------------------- nilai sumber daya

    @Test
    public void phoneDialogsAreAtLeastNinetyPercentWideInPortrait() {
        assertMinWidthFraction(PHONE_SW_DP, R.dimen.dialog_min_width_minor, MIN_PHONE_FRACTION);
    }

    /**
     * Lanskap sengaja lebih sempit daripada potret. Memberi lanskap persentase
     * yang sama menghasilkan dialog selebar layar penuh dengan satu kolom
     * isian membentang sendirian di tengahnya.
     */
    @Test
    public void phoneDialogsStayNarrowerInLandscapeThanInPortrait() {
        try (ActivityScenario<TestHostActivity> scenario =
                     ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                Context ctx = configFor(activity, PHONE_SW_DP);
                int screen = ctx.getResources().getDisplayMetrics().widthPixels;
                int minor = resolveWidth(ctx, R.dimen.dialog_min_width_minor, screen);
                int major = resolveWidth(ctx, R.dimen.dialog_min_width_major, screen);

                if (major >= minor) {
                    throw new AssertionError("Lebar lanskap (" + major + "px) tidak boleh"
                            + " sama atau lebih besar daripada potret (" + minor + "px)"
                            + " pada ponsel.");
                }
                if (major < screen * 0.70f) {
                    throw new AssertionError("Lebar lanskap " + major + "px terlalu sempit;"
                            + " minimal 70% dari " + screen + "px.");
                }
            });
        }
    }

    @Test
    public void wideScreenDialogsStopGrowingAndAreExpressedInDp() {
        try (ActivityScenario<TestHostActivity> scenario =
                     ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                Context ctx = configFor(activity, TABLET_SW_DP);
                TypedValue value = new TypedValue();
                ctx.getResources().getValue(R.dimen.dialog_min_width_minor, value, true);

                if (value.type == TypedValue.TYPE_FRACTION) {
                    throw new AssertionError("Pada layar lebar, lebar dialog harus dinyatakan"
                            + " dalam dp agar berhenti tumbuh - bukan sebagai persentase.");
                }

                int minimum = dpToPx(ctx, MIN_TABLET_DP);
                int actual = ctx.getResources().getDimensionPixelSize(R.dimen.dialog_min_width_minor);
                if (actual < minimum) {
                    throw new AssertionError("Lebar dialog tablet " + actual + "px di bawah"
                            + " lantai " + minimum + "px (" + MIN_TABLET_DP + "dp).");
                }
            });
        }
    }

    // ----------------------------------------------------- jendela sungguhan

    /**
     * Yang diukur di sini adalah jendela yang benar-benar tampil, bukan angka
     * dimennya. Sebuah tema bisa saja memuat nilai yang benar sementara dialog
     * yang muncul tetap sempit - misalnya karena atributnya dipasang pada
     * overlay yang tidak terpakai.
     */
    @Test
    public void aShownMaterialDialogFillsMostOfTheScreen() {
        assertShownDialogIsWide(true);
    }

    /**
     * Dialog androidx (AlertDialog.Builder) memakai overlay tema yang BERBEDA
     * dari MaterialAlertDialogBuilder. Sebelumnya hanya overlay Material yang
     * memuat aturan lebar, jadi dua popup dari layar yang sama tampil dengan
     * lebar berbeda.
     */
    @Test
    public void aShownAndroidxDialogIsJustAsWide() {
        assertShownDialogIsWide(false);
    }

    private void assertShownDialogIsWide(boolean material) {
        try (ActivityScenario<TestHostActivity> scenario =
                     ActivityScenario.launch(TestHostActivity.class)) {

            AtomicReference<Dialog> holder = new AtomicReference<>();
            AtomicInteger screenWidth = new AtomicInteger();

            scenario.onActivity(activity -> {
                TextView body = new TextView(activity);
                body.setText("x");

                Dialog dialog = material
                        ? new MaterialAlertDialogBuilder(activity)
                        .setTitle("Lebar")
                        .setView(body)
                        .setPositiveButton("OK", null)
                        .create()
                        : new androidx.appcompat.app.AlertDialog.Builder(activity)
                        .setTitle("Lebar")
                        .setView(body)
                        .setPositiveButton("OK", null)
                        .create();

                dialog.show();
                holder.set(dialog);
                screenWidth.set(activity.getResources().getDisplayMetrics().widthPixels);
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                Dialog dialog = holder.get();
                View decor = dialog.getWindow() != null
                        ? dialog.getWindow().getDecorView() : null;
                if (decor == null) {
                    throw new AssertionError("Dialog tidak punya jendela.");
                }

                int width = decor.getWidth();
                int minimum = Math.round(screenWidth.get() * MIN_PHONE_FRACTION);
                boolean wideScreen = activity.getResources().getConfiguration()
                        .smallestScreenWidthDp >= TABLET_SW_DP;

                // Di layar lebar lantainya memang bukan persentase; yang dijaga
                // di sana adalah nilai dp-nya, diperiksa uji terpisah di atas.
                if (!wideScreen && width < minimum) {
                    throw new AssertionError((material ? "Material" : "androidx")
                            + " dialog terukur " + width + "px dari layar "
                            + screenWidth.get() + "px - di bawah lantai " + minimum + "px."
                            + " Inilah gejala yang dikeluhkan: popup terasa kurus.");
                }

                dialog.dismiss();
            });
        }
    }

    // ------------------------------------------------------------- pembantu

    /**
     * Menyelesaikan nilai lebar seperti yang dilakukan platform: sebuah
     * pecahan diukur terhadap lebar layar, sebuah dimensi dibaca apa adanya.
     */
    private int resolveWidth(Context ctx, int dimenRes, int screenWidthPx) {
        TypedValue value = new TypedValue();
        ctx.getResources().getValue(dimenRes, value, true);

        if (value.type == TypedValue.TYPE_FRACTION) {
            return Math.round(value.getFraction(screenWidthPx, screenWidthPx));
        }
        return ctx.getResources().getDimensionPixelSize(dimenRes);
    }

    private void assertMinWidthFraction(int smallestWidthDp, int dimenRes, float fraction) {
        try (ActivityScenario<TestHostActivity> scenario =
                     ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                Context ctx = configFor(activity, smallestWidthDp);
                int screen = ctx.getResources().getDisplayMetrics().widthPixels;
                int actual = resolveWidth(ctx, dimenRes, screen);
                int minimum = Math.round(screen * fraction);

                if (actual < minimum) {
                    throw new AssertionError("Lebar minimum dialog pada sw" + smallestWidthDp
                            + "dp hanya " + actual + "px dari layar " + screen + "px"
                            + " (" + Math.round(100f * actual / screen) + "%), di bawah lantai "
                            + Math.round(fraction * 100) + "%.");
                }
            });
        }
    }

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
