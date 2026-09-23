package com.valdker.pos.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.valdker.pos.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Menjaga agar layar kasir benar-benar muat di setiap ukuran layar, bukan
 * hanya di ukuran emulator yang kebetulan dipakai saat menulisnya.
 *
 * <p>{@link PosTextScalingTest} menguji sumbu yang berbeda - ukuran huruf
 * sistem - tetapi selalu pada satu ukuran layar: milik perangkat yang sedang
 * menjalankannya. Justru ukuran layar yang paling sering membuat kasir rusak,
 * dan rusaknya senyap: varian {@code layout-land/} dan {@code layout-sw600dp/}
 * ikut dikompilasi dengan baik meski isinya sudah tertinggal beberapa desain,
 * dan pemilik toko yang memakai tablet melihat aplikasi yang sama sekali
 * berbeda dari yang terlihat di ponsel pengembang.
 *
 * <p>Uji ini menginflate layar kasir pada tujuh ukuran layar nyata - dari
 * ponsel 320dp sampai tablet 1600dp, tegak dan mendatar - lewat
 * {@link Context#createConfigurationContext}, sehingga Android benar-benar
 * memilih kelompok sumber daya yang sama seperti pada perangkat sungguhan,
 * lalu memeriksa empat hal:
 *
 * <ol>
 *   <li>tidak ada yang melebar melewati tepi kanan layar;
 *   <li>tidak ada yang terdorong keluar tepi bawah - terutama tombol bayar,
 *       yang kalau hilang membuat transaksi tidak bisa diselesaikan sama
 *       sekali;
 *   <li>blok yang wajib terlihat benar-benar punya tinggi - daftar item
 *       pernah tergencet sampai nol tanpa galat apa pun, karena
 *       {@code layout_weight} membagi sisa ruang yang bernilai negatif;
 *   <li>teks tidak terpotong, diperiksa ulang di setiap ukuran layar.
 * </ol>
 *
 * <p>Diperiksa pada skala huruf baku dan 1,3x sekaligus, karena layar sempit
 * dengan huruf besar adalah gabungan terburuk sekaligus yang paling lazim:
 * ponsel kecil milik kasir yang matanya sudah tidak muda.
 */
@RunWith(AndroidJUnit4.class)
public class PosResponsiveLayoutTest {

    /** Ukuran layar yang benar-benar dipakai pemilik toko, dalam dp. */
    private static final int[][] SCREENS = {
            {320, 568},   // ponsel kecil yang masih beredar
            {360, 640},   // ponsel acuan
            {412, 915},   // ponsel besar masa kini
            {640, 360},   // ponsel dimiringkan - tinggi paling kritis
            {800, 1280},  // tablet 10 inci tegak
            {1280, 800},  // tablet 10 inci mendatar
            {1600, 1000}, // tablet 13 inci
    };

    private static final float[] FONT_SCALES = {1f, 1.3f};

    private static final String SAMPLE = "Pelanggan Umum";

    /**
     * Layar kasir berikut komponen bersamanya. Yang disebut di sini adalah
     * NAMA layout, bukan berkasnya: Android sendiri yang memilih antara
     * {@code layout/}, {@code layout-land/}, dan {@code layout-sw600dp/} -
     * persis seperti di perangkat - sehingga varian yang tertinggal ikut
     * terperiksa tanpa perlu disebut satu per satu.
     */
    private static final int[] POS_LAYOUTS = {
            R.layout.activity_main,
            R.layout.fragment_retail_pos,
            R.layout.fragment_workshop_pos,
            R.layout.fragment_cart,
            R.layout.view_pos_header_resto,
            R.layout.view_pos_topbar,
            R.layout.view_pos_bon_row,
            R.layout.view_pos_list,
            R.layout.view_pos_checkout,
            R.layout.view_pos_category_bar,
    };

    /**
     * View yang kehilangan tingginya berarti layar kasir berhenti berguna,
     * meski tidak ada galat apa pun yang timbul. Diperiksa hanya jika id-nya
     * memang ada pada varian yang terpilih.
     */
    private static final int[] MUST_HAVE_HEIGHT = {
            R.id.posListSection,
            R.id.layoutItemsContainer,
            R.id.btnCheckout,
            R.id.bottomBar,
    };

    @Test
    public void posLayoutsFitEveryScreenSize() {
        List<String> problems = new ArrayList<>();
        try (ActivityScenario<TestHostActivity> scenario =
                     ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                for (int[] screen : SCREENS) {
                    for (float fontScale : FONT_SCALES) {
                        check(activity, screen[0], screen[1], fontScale, problems);
                    }
                }
            });
        }
        if (!problems.isEmpty()) {
            throw new AssertionError("Layar kasir tidak muat di sebagian ukuran layar:\n  "
                    + String.join("\n  ", problems));
        }
    }

    private void check(TestHostActivity activity, int widthDp, int heightDp,
                       float fontScale, List<String> problems) {
        Configuration config = new Configuration(activity.getResources().getConfiguration());
        config.screenWidthDp = widthDp;
        config.screenHeightDp = heightDp;
        config.smallestScreenWidthDp = Math.min(widthDp, heightDp);
        config.orientation = widthDp >= heightDp
                ? Configuration.ORIENTATION_LANDSCAPE
                : Configuration.ORIENTATION_PORTRAIT;
        config.fontScale = fontScale;

        Context scaled = activity.createConfigurationContext(config);
        scaled.setTheme(R.style.Theme_ValdKer);

        // cloneInContext mempertahankan factory AppCompat, jadi <Switch> dan
        // komponen Material tetap dipetakan seperti di dalam aplikasi.
        LayoutInflater inflater = activity.getLayoutInflater().cloneInContext(scaled);
        FrameLayout parent = activity.container();

        // Kerapatan sengaja tidak ikut diubah, jadi dp tetap dp: perkalian di
        // bawah ini menghasilkan piksel yang sama seperti perangkat sungguhan
        // pada kerapatan mana pun.
        float density = scaled.getResources().getDisplayMetrics().density;
        int widthPx = Math.round(widthDp * density);
        int heightPx = Math.round(heightDp * density);

        for (int layout : POS_LAYOUTS) {
            String label = String.format(Locale.US, "%s @ %dx%ddp huruf %.1fx",
                    activity.getResources().getResourceEntryName(layout),
                    widthDp, heightDp, fontScale);

            View view = inflater.inflate(layout, parent, false);
            fillSampleText(view);

            // Layar penuh diukur EXACTLY pada kedua sumbu; komponen bersama
            // hanya pada lebarnya, karena tingginya memang mengikuti isi dan
            // ditentukan oleh layar yang memakainya.
            boolean fullScreen = isFullScreen(layout);
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPx,
                            fullScreen ? View.MeasureSpec.EXACTLY : View.MeasureSpec.AT_MOST));
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());

            if (view.getMeasuredWidth() > widthPx) {
                problems.add(label + ": akarnya selebar " + view.getMeasuredWidth()
                        + "px sementara layarnya " + widthPx + "px");
            }
            collectOverflow(activity, label, view, widthPx, problems);
            if (fullScreen) {
                collectPushedOffBottom(activity, label, view, heightPx, problems);
                collectCollapsed(activity, label, view, problems);
            }
            collectClippedText(activity, label, view, problems);
        }
    }

    /**
     * Hanya layar penuh yang boleh dituntut muat setinggi layar. Komponen
     * bersama seperti bilah total memang setinggi isinya.
     */
    private boolean isFullScreen(int layout) {
        return layout == R.layout.activity_main
                || layout == R.layout.fragment_retail_pos
                || layout == R.layout.fragment_workshop_pos;
    }

    private void collectOverflow(TestHostActivity activity, String label, View view,
                                 int widthPx, List<String> problems) {
        if (!(view instanceof ViewGroup)) return;
        if (view.getVisibility() == View.GONE) return;

        // Isi yang memang digulir mendatar boleh lebih lebar daripada layar -
        // itulah gunanya. Yang dicari di sini adalah isi yang TIDAK bisa
        // digulir tetapi tetap melewati tepi.
        if (view instanceof HorizontalScrollView || view instanceof RecyclerView) return;

        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            int right = absoluteLeft(child, view) + child.getWidth();
            if (right > widthPx + 1) {
                problems.add(label + " / " + idOf(activity, child)
                        + ": tepi kanannya di " + right
                        + "px, melewati tepi layar " + widthPx + "px");
            }
            collectOverflow(activity, label, child, widthPx, problems);
        }
    }

    private void collectPushedOffBottom(TestHostActivity activity, String label, View view,
                                        int heightPx, List<String> problems) {
        View pay = view.findViewById(R.id.btnCheckout);
        if (pay == null || pay.getVisibility() == View.GONE) return;
        int bottom = absoluteTop(pay, view) + pay.getHeight();
        if (bottom > heightPx + 1) {
            problems.add(label + " / btnCheckout: tepi bawahnya di " + bottom
                    + "px, di luar layar setinggi " + heightPx + "px"
                    + " - transaksi tidak bisa diselesaikan");
        }
    }

    private void collectCollapsed(TestHostActivity activity, String label, View view,
                                  List<String> problems) {
        for (int id : MUST_HAVE_HEIGHT) {
            View target = view.findViewById(id);
            if (target == null || target.getVisibility() == View.GONE) continue;
            if (target.getHeight() <= 0 || target.getWidth() <= 0) {
                problems.add(label + " / " + activity.getResources().getResourceEntryName(id)
                        + ": tergencet jadi " + target.getWidth() + "x" + target.getHeight()
                        + "px");
            }
        }
    }

    private void collectClippedText(TestHostActivity activity, String label, View view,
                                    List<String> problems) {
        if (view.getVisibility() == View.GONE) return;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectClippedText(activity, label, group.getChildAt(i), problems);
            }
        }
        if (!(view instanceof TextView)) return;

        TextView text = (TextView) view;
        if (text.getText() == null || text.getText().length() == 0) return;
        Layout textLayout = text.getLayout();
        if (textLayout == null) return;

        // Tinggi atau lebar nol berarti induknya memang tidak memberi ruang
        // pada pengukuran sintetis ini - bukan teks yang terpotong.
        if (text.getHeight() == 0 || text.getWidth() == 0) return;

        int needed = textLayout.getHeight() + text.getPaddingTop() + text.getPaddingBottom();
        if (text.getHeight() >= needed) return;
        problems.add(label + " / " + idOf(activity, text)
                + ": tinggi kotaknya " + text.getHeight() + "px, barisnya butuh " + needed + "px"
                + " - lebar " + text.getWidth() + "px, " + textLayout.getLineCount() + " baris"
                + " (\"" + text.getText() + "\")");
    }

    private void fillSampleText(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                fillSampleText(group.getChildAt(i));
            }
            return;
        }
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (isIconOnlyButton(text)) return;
            if (text.getText() == null || text.getText().length() == 0) {
                text.setText(SAMPLE);
            }
        }
    }

    /**
     * Tombol ikon bundar memang sengaja tanpa teks; ukurannya mengikuti ikon,
     * bukan huruf. Mengisinya dengan teks contoh hanya akan melaporkan
     * kegagalan palsu.
     */
    private boolean isIconOnlyButton(TextView view) {
        return view instanceof com.google.android.material.button.MaterialButton
                && ((com.google.android.material.button.MaterialButton) view).getIcon() != null
                && (view.getText() == null || view.getText().length() == 0);
    }

    private int absoluteLeft(View view, View root) {
        int left = 0;
        for (View v = view; v != null && v != root; ) {
            left += v.getLeft();
            ViewParent p = v.getParent();
            v = (p instanceof View) ? (View) p : null;
        }
        return left;
    }

    private int absoluteTop(View view, View root) {
        int top = 0;
        for (View v = view; v != null && v != root; ) {
            top += v.getTop();
            ViewParent p = v.getParent();
            v = (p instanceof View) ? (View) p : null;
        }
        return top;
    }

    private String idOf(TestHostActivity activity, View view) {
        if (view.getId() == View.NO_ID) return view.getClass().getSimpleName();
        try {
            return activity.getResources().getResourceEntryName(view.getId());
        } catch (RuntimeException e) {
            return view.getClass().getSimpleName();
        }
    }
}
