package com.valdker.pos.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.valdker.pos.R;
import com.valdker.pos.ui.common.SystemBars;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Menjaga agar kepala ungu ketiga kasir benar-benar sampai ke tepi atas layar,
 * di belakang jam dan baterai.
 *
 * <p>Sejak targetSdk 36, Android 15+ mengabaikan {@code setStatusBarColor}:
 * warna di belakang bilah status hanya bisa datang dari tata letak. Pola yang
 * dipakai aplikasi ini adalah sebuah View setinggi nol di dalam blok ungu,
 * {@code posHeaderScrim}, yang ditumbuhkan setinggi inset bilah status oleh
 * {@link SystemBars#fitStatusScrim(View)}. Karena ia berada DI DALAM blok
 * ungu, warnanya otomatis sama - tidak ada dua lapis ungu yang bisa berbeda
 * tipis, dan tidak ada strip berwarna lain yang menyelinap di atasnya.
 *
 * <p>Sebelum uji ini ada, hanya kasir retail yang memakai pola itu. Kasir
 * restoran memakai cara lain: seluruh kepalanya DIDORONG ke bawah sejauh
 * tinggi bilah status, dan yang tersisa di belakang bilah status adalah
 * {@code statusBarScrim} milik activity_main yang berwarna tinta gelap.
 * Hasilnya ungunya berhenti tepat di bawah jam dan baterai. Kasir bengkel
 * memakai pola yang benar tetapi dengan id {@code statusBarScrim} - nama yang
 * sama persis dengan View bertinta gelap milik activity_main, tempat kasir
 * bengkel itu sendiri hidup - sehingga dua View berbeda berbagi satu id dalam
 * satu pohon view, dan siapa yang ditemukan {@code findViewById} bergantung
 * pada urutan penelusuran.
 *
 * <p>Keduanya tidak menimbulkan galat apa pun. Yang terlihat hanyalah strip
 * berwarna salah setinggi beberapa dp di tepi atas layar - hal yang tidak
 * pernah gagal di uji mana pun dan tidak pernah membuat siapa pun membuka
 * laporan bug, tapi langsung terlihat oleh pemilik toko yang memakai ketiga
 * kasir bergantian.
 */
@RunWith(AndroidJUnit4.class)
public class PosStatusBarHeaderTest {

    /** Tinggi bilah status yang lazim, dalam dp. */
    private static final int STATUS_BAR_DP = 24;

    /** Layar kasir berikut id blok ungu yang harus menempel ke tepi atas. */
    private static final int[][] POS_SCREENS = {
            {R.layout.fragment_retail_pos, R.id.topBar},
            {R.layout.fragment_workshop_pos, R.id.cardWorkshopHeader},
            {R.layout.activity_main, R.id.nativeHeader},
    };

    @Test
    public void everyPosHeaderPaintsBehindTheStatusBar() {
        List<String> problems = new ArrayList<>();
        try (ActivityScenario<TestHostActivity> scenario =
                     ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                LayoutInflater inflater = activity.getLayoutInflater();
                FrameLayout parent = activity.container();
                float density = activity.getResources().getDisplayMetrics().density;
                int statusPx = Math.round(STATUS_BAR_DP * density);

                for (int[] screen : POS_SCREENS) {
                    String name = activity.getResources().getResourceEntryName(screen[0]);
                    View root = inflater.inflate(screen[0], parent, false);

                    View header = root.findViewById(screen[1]);
                    if (header == null) {
                        problems.add(name + ": blok ungu "
                                + activity.getResources().getResourceEntryName(screen[1])
                                + " tidak ditemukan");
                        continue;
                    }

                    View scrim = header.findViewById(R.id.posHeaderScrim);
                    if (scrim == null) {
                        problems.add(name + ": tidak ada posHeaderScrim DI DALAM blok ungu -"
                                + " tanpa itu warna di belakang bilah status datang dari"
                                + " tempat lain, dan di Android 15+ tidak bisa lagi diatur"
                                + " lewat setStatusBarColor");
                        continue;
                    }

                    // Strip harus tumbuh persis setinggi inset, dan hanya oleh
                    // inset - tinggi awalnya nol supaya perangkat tanpa bilah
                    // status tidak kehilangan ruang.
                    if (scrim.getLayoutParams().height != 0) {
                        problems.add(name + ": posHeaderScrim ditulis setinggi "
                                + scrim.getLayoutParams().height + "px di layout, seharusnya 0dp"
                                + " - tingginya milik inset, bukan angka tetap");
                    }

                    SystemBars.fitStatusScrim(scrim);
                    ViewCompat.dispatchApplyWindowInsets(scrim, insetsWithStatusBar(statusPx));

                    if (scrim.getLayoutParams().height != statusPx) {
                        problems.add(name + ": posHeaderScrim setinggi "
                                + scrim.getLayoutParams().height + "px sesudah inset "
                                + statusPx + "px - strip di belakang bilah status tidak terisi");
                    }

                    // Blok ungunya sendiri harus mulai di y=0. Kalau ia
                    // didorong ke bawah, yang mengisi celah di atasnya adalah
                    // latar lain - persis cacat yang dilaporkan untuk kasir
                    // restoran.
                    int widthPx = Math.round(360 * density);
                    int heightPx = Math.round(640 * density);
                    root.measure(
                            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
                    root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());

                    int top = absoluteTop(header, root);
                    if (top != 0) {
                        problems.add(name + ": blok ungu mulai di y=" + top + "px, bukan 0"
                                + " - ada celah berlatar lain di atasnya");
                    }
                }
            });
        }
        if (!problems.isEmpty()) {
            throw new AssertionError("Kepala kasir tidak menempel ke bilah status:\n  "
                    + String.join("\n  ", problems));
        }
    }

    private WindowInsetsCompat insetsWithStatusBar(int statusPx) {
        return new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(),
                        androidx.core.graphics.Insets.of(0, statusPx, 0, 0))
                .build();
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
}
