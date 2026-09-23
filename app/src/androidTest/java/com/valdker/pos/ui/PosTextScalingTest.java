package com.valdker.pos.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.valdker.pos.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Menjaga agar teks di layar kasir tidak terpotong ketika pemilik toko
 * menaikkan ukuran huruf sistem.
 *
 * <p>Ini sumbu keresponsifan yang paling sering terlewat. Angka dp sudah
 * mengurus perbedaan kerapatan layar, jadi tombol 48dp memang tetap 48dp di
 * tablet 7 maupun 13 inci. Yang tidak diurus dp adalah ISI: ukuran huruf
 * diukur dalam sp, dan sp ikut membesar begitu pengguna menggeser "Ukuran
 * huruf" di setelan. Setiap {@code android:layout_height} berangka pada
 * TextView, Chip, atau tombol adalah kotak yang tidak ikut membesar - maka
 * hurufnya yang dipotong. Di tablet kasir yang dipakai bergantian beberapa
 * orang, setelan itu memang kerap dinaikkan.
 *
 * <p>Uji ini menginflate layar kasir pada skala huruf bawaan dan pada 1,3x,
 * mengisi setiap TextView dengan teks contoh, mengukurnya selebar layar
 * sungguhan, lalu membandingkan tinggi yang tersedia dengan tinggi yang
 * dibutuhkan barisnya. Sebelum ukuran mati diganti minHeight, varian tablet
 * kasir bengkel gagal di sini: baris ringkasan kendaraan dipaku 19dp,
 * sementara teks 13sp pada skala 1,3x membutuhkan sekitar 24dp.
 */
@RunWith(AndroidJUnit4.class)
public class PosTextScalingTest {

    /** Skala huruf terbesar yang masih lazim dipilih pemilik toko. */
    private static final float LARGE_FONT_SCALE = 1.3f;

    /** Teks contoh untuk kolom yang di layout hanya berisi tools:text. */
    private static final String SAMPLE = "Pelanggan Umum";

    private static final int[] POS_LAYOUTS = {
            R.layout.activity_main,
            R.layout.fragment_retail_pos,
            R.layout.fragment_workshop_pos,
            R.layout.fragment_cart,
            R.layout.fragment_products,
            R.layout.view_pos_topbar,
            R.layout.view_pos_bon_row,
            R.layout.view_pos_list,
            R.layout.view_pos_checkout,
            R.layout.view_pos_category_bar,
            R.layout.item_product_grid,
            R.layout.item_product_list,
            R.layout.item_cart,
            R.layout.item_category_chip,
            R.layout.item_workshop_cart_line,
            R.layout.dialog_native_checkout,
            R.layout.item_split_payment,
    };

    @Test
    public void posTextFitsAtDefaultFontScale() {
        assertNothingClipped(1f);
    }

    @Test
    public void posTextFitsAtLargeFontScale() {
        assertNothingClipped(LARGE_FONT_SCALE);
    }

    private void assertNothingClipped(float fontScale) {
        List<String> clipped = new ArrayList<>();
        try (ActivityScenario<TestHostActivity> scenario =
                     ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                Configuration config =
                        new Configuration(activity.getResources().getConfiguration());
                config.fontScale = fontScale;
                Context scaled = activity.createConfigurationContext(config);
                scaled.setTheme(R.style.Theme_ValdKer);

                // cloneInContext mempertahankan factory AppCompat, jadi
                // <Switch> dan komponen Material tetap dipetakan seperti di
                // dalam aplikasi - hanya skala hurufnya yang berbeda.
                LayoutInflater inflater =
                        activity.getLayoutInflater().cloneInContext(scaled);
                FrameLayout parent = activity.container();

                int widthPx = activity.getResources().getDisplayMetrics().widthPixels;
                int heightPx = activity.getResources().getDisplayMetrics().heightPixels;

                for (int layout : POS_LAYOUTS) {
                    String name = activity.getResources().getResourceEntryName(layout);
                    View view = inflater.inflate(layout, parent, false);
                    fillSampleText(view);
                    view.measure(
                            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.AT_MOST));
                    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                    collectClipped(activity, name, view, clipped);
                }
            });
        }
        if (!clipped.isEmpty()) {
            throw new AssertionError("Teks terpotong pada skala huruf " + fontScale + ":\n  "
                    + String.join("\n  ", clipped));
        }
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
     * Tombol ikon bundar - tombol "+" pada kartu menu, misalnya - memang
     * sengaja tanpa teks; ukurannya mengikuti ikon, bukan huruf. Mengisinya
     * dengan teks contoh hanya akan melaporkan kegagalan palsu.
     */
    private boolean isIconOnlyButton(TextView view) {
        return view instanceof com.google.android.material.button.MaterialButton
                && ((com.google.android.material.button.MaterialButton) view).getIcon() != null
                && (view.getText() == null || view.getText().length() == 0);
    }

    private void collectClipped(TestHostActivity activity, String layoutName, View view,
                                List<String> out) {
        if (view.getVisibility() == View.GONE) return;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectClipped(activity, layoutName, group.getChildAt(i), out);
            }
        }
        if (!(view instanceof TextView)) return;

        TextView text = (TextView) view;
        if (text.getText() == null || text.getText().length() == 0) return;
        Layout textLayout = text.getLayout();
        if (textLayout == null) return;

        // Tinggi atau lebar nol berarti induknya memang tidak memberi ruang
        // pada pengukuran sintetis ini - misalnya isi kartu kasir retail yang
        // tingginya berasal dari rantai constraint ke RecyclerView kosong.
        // Itu bukan pemotongan teks; yang dicari uji ini adalah kotak yang
        // ADA tetapi lebih pendek daripada barisnya.
        if (text.getHeight() == 0 || text.getWidth() == 0) return;

        int needed = textLayout.getHeight() + text.getPaddingTop() + text.getPaddingBottom();
        if (text.getHeight() >= needed) return;

        out.add(layoutName + " / " + idOf(activity, text)
                + ": tersedia " + text.getHeight() + "px, dibutuhkan " + needed + "px"
                + " (\"" + text.getText() + "\")");
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
