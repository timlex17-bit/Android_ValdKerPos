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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Menjalankan pemeriksaan keresponsifan pada SELURUH tata letak aplikasi.
 *
 * <p>{@link PosResponsiveLayoutTest} memeriksa layar kasir secara mendalam -
 * tujuh ukuran layar, tombol bayar yang terdorong keluar, blok yang tergencet
 * jadi nol. Uji ini melengkapinya dari arah berbeda: lebih dangkal, tetapi
 * menyentuh setiap berkas layout yang ada, termasuk layar yang hanya terbuka
 * untuk peran tertentu dan yang hampir tidak pernah dibuka siapa pun.
 *
 * <p>Daftar layout-nya TIDAK ditulis tangan. Ia dibaca dari {@code R.layout}
 * lewat refleksi, jadi berkas layout yang ditambahkan besok ikut terperiksa
 * tanpa ada yang perlu mengingat untuk mendaftarkannya - dan daftar yang harus
 * diingat adalah daftar yang cepat tertinggal.
 *
 * <p>Tiga hal yang diperiksa, dan ketiganya benar-benar pernah terjadi di
 * aplikasi ini:
 *
 * <ol>
 *   <li><b>Tergambar di luar tepi layar.</b> Baris laporan laba memberi bobot
 *       hanya pada kolom tanggal; tiga kolom angka di kanannya memakai
 *       wrap_content dan mendorong diri keluar layar begitu angkanya menjadi
 *       nilai sungguhan - kolom laba, satu-satunya yang dicari pemilik toko di
 *       baris itu, yang pertama hilang.
 *   <li><b>Teks terpotong oleh kotak bertinggi mati.</b> Tombol keluar di
 *       dasbor dipaku 32dp; pada skala huruf 1,3x barisnya butuh 33dp.
 *       Selisihnya tiga piksel dan terbaca sebagai huruf yang gepeng, bukan
 *       sebagai cacat - jenis hal yang tidak pernah dilaporkan siapa pun.
 *   <li><b>Gagal inflate.</b> Gaya yang mewarisi induk salah atau atribut yang
 *       hanya berlaku pada induk tertentu lolos aapt, lalu meledak di tangan
 *       pengguna.
 * </ol>
 *
 * <p>Tombol yang memang hanya berisi ikon dikecualikan dari pemeriksaan teks:
 * mengisinya dengan teks contoh menghasilkan kegagalan palsu pada tombol yang
 * seumur hidupnya tidak pernah bertulisan.
 */
@RunWith(AndroidJUnit4.class)
public class AppLayoutResponsiveTest {

    /** Ponsel tersempit, ponsel acuan, dan tablet tegak. */
    private static final int[][] SCREENS = {{320, 568}, {360, 640}, {800, 1280}};

    private static final float[] FONT_SCALES = {1f, 1.3f};

    private static final String SAMPLE = "Pelanggan Umum";

    @Test
    public void everyLayoutFitsTheNarrowestPhone() {
        List<String> problems = new ArrayList<>();

        try (ActivityScenario<TestHostActivity> scenario =
                     ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                List<Integer> ids = new ArrayList<>();
                List<String> names = new ArrayList<>();
                for (Field f : R.layout.class.getFields()) {
                    try {
                        ids.add(f.getInt(null));
                        names.add(f.getName());
                    } catch (IllegalAccessException ignored) {
                        // Tidak terjadi: seluruh field R.layout publik dan statis.
                    }
                }

                FrameLayout parent = activity.container();
                for (int[] screen : SCREENS) {
                    for (float fontScale : FONT_SCALES) {
                        Configuration config =
                                new Configuration(activity.getResources().getConfiguration());
                        config.screenWidthDp = screen[0];
                        config.screenHeightDp = screen[1];
                        config.smallestScreenWidthDp = Math.min(screen[0], screen[1]);
                        config.orientation = screen[0] >= screen[1]
                                ? Configuration.ORIENTATION_LANDSCAPE
                                : Configuration.ORIENTATION_PORTRAIT;
                        config.fontScale = fontScale;

                        Context scaled = activity.createConfigurationContext(config);
                        scaled.setTheme(R.style.Theme_ValdKer);
                        LayoutInflater inflater =
                                activity.getLayoutInflater().cloneInContext(scaled);

                        float density = scaled.getResources().getDisplayMetrics().density;
                        int widthPx = Math.round(screen[0] * density);
                        int heightPx = Math.round(screen[1] * density);

                        for (int i = 0; i < ids.size(); i++) {
                            String label = String.format(Locale.US, "%s @ %ddp huruf %.1fx",
                                    names.get(i), screen[0], fontScale);
                            View view;
                            try {
                                view = inflater.inflate(ids.get(i), parent, false);
                            } catch (Throwable t) {
                                problems.add(label + ": gagal inflate - "
                                        + t.getClass().getSimpleName() + ": " + t.getMessage());
                                continue;
                            }
                            fillSampleText(view);
                            view.measure(
                                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                                    View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.AT_MOST));
                            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());

                            collectOverflow(activity, label, view, view, widthPx, problems);
                            collectClipped(activity, label, view, problems);
                        }
                    }
                }
            });
        }

        if (!problems.isEmpty()) {
            throw new AssertionError("Tata letak tidak muat di layar sempit:\n  "
                    + String.join("\n  ", problems));
        }
    }

    private void collectOverflow(TestHostActivity activity, String label, View view, View root,
                                 int widthPx, List<String> out) {
        if (!(view instanceof ViewGroup) || view.getVisibility() == View.GONE) return;
        // Isi yang memang digulir mendatar boleh lebih lebar daripada layar.
        if (view instanceof HorizontalScrollView || view instanceof RecyclerView) return;

        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            int left = 0;
            for (View v = child; v != null && v != root; ) {
                left += v.getLeft();
                ViewParent p = v.getParent();
                v = (p instanceof View) ? (View) p : null;
            }
            if (left + child.getWidth() > widthPx + 1) {
                out.add(label + " / " + idOf(activity, child) + ": tepi kanannya di "
                        + (left + child.getWidth()) + "px, layarnya " + widthPx + "px");
            }
            collectOverflow(activity, label, child, root, widthPx, out);
        }
    }

    private void collectClipped(TestHostActivity activity, String label, View view,
                                List<String> out) {
        if (view.getVisibility() == View.GONE) return;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectClipped(activity, label, group.getChildAt(i), out);
            }
        }
        if (!(view instanceof TextView)) return;

        TextView text = (TextView) view;
        if (text.getText() == null || text.getText().length() == 0) return;
        Layout textLayout = text.getLayout();
        if (textLayout == null || text.getHeight() == 0 || text.getWidth() == 0) return;

        int needed = textLayout.getHeight() + text.getPaddingTop() + text.getPaddingBottom();
        if (text.getHeight() >= needed) return;
        out.add(label + " / " + idOf(activity, text) + ": tinggi kotaknya " + text.getHeight()
                + "px, barisnya butuh " + needed + "px");
    }

    private void fillSampleText(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) fillSampleText(group.getChildAt(i));
            return;
        }
        if (!(view instanceof TextView)) return;
        TextView text = (TextView) view;
        if (isIconOnlyButton(text)) return;
        if (text.getText() == null || text.getText().length() == 0) text.setText(SAMPLE);
    }

    private boolean isIconOnlyButton(TextView view) {
        return view instanceof com.google.android.material.button.MaterialButton
                && ((com.google.android.material.button.MaterialButton) view).getIcon() != null
                && (view.getText() == null || view.getText().length() == 0);
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
