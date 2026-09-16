package com.valdker.pos.ui.common;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.valdker.pos.R;

/**
 * Satu aturan bilah sistem untuk seluruh aplikasi.
 *
 * <p>Kenapa ini perlu ada: sejak targetSdk 36, Android 15+ mengabaikan
 * {@code android:statusBarColor} dan memaksa jendela menggambar sampai ke tepi
 * layar. Artinya yang terlihat di balik bilah status bukan lagi warna yang
 * diminta tema, melainkan apa pun yang kebetulan ada di baris teratas layout.
 * Sebelum ini setiap layar memecahkan masalah itu sendiri - ada yang memanggil
 * {@code setStatusBarColor} (tidak berpengaruh lagi), ada yang memakai
 * {@code SystemBarsFix}, ada yang tidak melakukan apa-apa - sehingga bilah
 * status tampil ungu di satu layar, putih di layar berikutnya, dan di beberapa
 * layar ikonnya putih di atas latar putih alias hilang.
 *
 * <p>Aturan yang dipakai sekarang, sama di semua versi Android dan semua
 * ukuran layar: bilah status selalu ungu merek dengan ikon terang, bilah
 * navigasi selalu putih dengan ikon gelap. Warnanya dihasilkan oleh layout
 * itu sendiri - {@link #padTopBar(View)} menumbuhkan bilah atas yang memang
 * sudah ungu sampai ke belakang bilah status, dan layar yang bagian atasnya
 * terang memakai {@link #fitStatusScrim(View)} untuk menaruh strip ungu
 * setinggi bilah status. Keduanya bekerja tanpa bergantung pada API yang
 * sudah tidak berlaku.
 */
public final class SystemBars {

    private SystemBars() {
    }

    /**
     * Menyiapkan jendela: gambar sampai tepi, ikon bilah status terang, ikon
     * bilah navigasi gelap. Panggil sekali di {@code onCreate}, setelah
     * {@code setContentView}.
     */
    public static void apply(@Nullable Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;

        // Sisa flag mode layar penuh dari layar lain bisa menyembunyikan bilah
        // status sepenuhnya; bersihkan supaya setiap layar mulai dari keadaan
        // yang sama.
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        WindowCompat.setDecorFitsSystemWindows(window, false);

        // Masih berlaku di Android 14 ke bawah; di Android 15+ diabaikan dan
        // warnanya datang dari padTopBar()/fitStatusScrim().
        window.setStatusBarColor(ContextCompat.getColor(activity, R.color.status_bar_brand));
        window.setNavigationBarColor(ContextCompat.getColor(activity, R.color.surface));

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.show(WindowInsetsCompat.Type.statusBars());
        controller.show(WindowInsetsCompat.Type.navigationBars());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(true);
    }

    /**
     * Menumbuhkan bilah atas sampai ke belakang bilah status.
     *
     * <p>Sisi kiri/kanan ikut ditambah karena pada mode lanskap takik kamera
     * berada di salah satu tepi panjang; tanpa ini tombol kembali bisa
     * tertutup takik di ponsel yang layarnya berlubang.
     */
    public static void padTopBar(@Nullable View topBar) {
        if (topBar == null) return;

        final int baseLeft = topBar.getPaddingLeft();
        final int baseTop = topBar.getPaddingTop();
        final int baseRight = topBar.getPaddingRight();
        final int baseBottom = topBar.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(topBar, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(
                    baseLeft + bars.left,
                    baseTop + bars.top,
                    baseRight + bars.right,
                    baseBottom
            );
            return insets;
        });

        ViewCompat.requestApplyInsets(topBar);
    }

    /**
     * Menjauhkan isi dari bilah status tanpa mewarnai apa pun.
     *
     * <p>Dipakai layar yang LATARNYA sendiri sudah ungu sampai ke tepi atas -
     * layar masuk dan layar aktivasi - sehingga tidak perlu View strip
     * tersendiri: yang terlihat di balik bilah status adalah latar itu, dan
     * tugas yang tersisa hanyalah menurunkan isinya supaya tidak tertimpa jam
     * dan ikon baterai. Sisi kiri/kanan ikut ditambah karena pada mode lanskap
     * takik kamera berada di salah satu tepi panjang.
     *
     * <p>Bedanya dengan {@link #padTopBar(View)} hanya niatnya: yang itu
     * menumbuhkan bilah atas yang memang berwarna, yang ini sekadar memberi
     * ruang. Perilakunya sengaja sama persis supaya tidak ada dua aturan
     * berbeda tentang takik.
     */
    public static void padTop(@Nullable View view) {
        padTopBar(view);
    }

    /**
     * Memberi tinggi setinggi bilah status pada sebuah View polos. Dipakai
     * layar yang bagian atasnya terang (login, onboarding) supaya strip di
     * belakang bilah status tetap ungu seperti layar lain.
     *
     * <p>View-nya ditulis di layout dengan {@code layout_height="0dp"}; di
     * perangkat tanpa bilah status ia tetap nol dan tidak memakan ruang.
     */
    public static void fitStatusScrim(@Nullable View scrim) {
        if (scrim == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(scrim, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null && lp.height != top) {
                lp.height = top;
                v.setLayoutParams(lp);
            }
            return insets;
        });

        ViewCompat.requestApplyInsets(scrim);
    }

    /**
     * Menjauhkan isi dari bilah navigasi / batang gestur. Dipakai pada wadah
     * terbawah layar (bilah tombol, panel total, daftar tanpa bilah bawah).
     */
    public static void padBottom(@Nullable View view) {
        padBottom(view, 0);
    }

    public static void padBottom(@Nullable View view, int extraPx) {
        if (view == null) return;

        final int baseLeft = view.getPaddingLeft();
        final int baseTop = view.getPaddingTop();
        final int baseRight = view.getPaddingRight();
        final int baseBottom = view.getPaddingBottom();
        final int extra = Math.max(0, extraPx);

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(
                    baseLeft + bars.left,
                    baseTop,
                    baseRight + bars.right,
                    baseBottom + bars.bottom + extra
            );
            return insets;
        });

        ViewCompat.requestApplyInsets(view);
    }

    /**
     * Seperti {@link #padBottom(View)} tapi juga ikut naik saat papan ketik
     * muncul. Dipakai bilah input (obrolan, pencarian di dasar layar) supaya
     * kolom ketiknya tidak tertutup papan ketik di perangkat yang jendelanya
     * tidak diubah ukurannya.
     */
    public static void padBottomWithIme(@Nullable View view) {
        if (view == null) return;

        final int baseLeft = view.getPaddingLeft();
        final int baseTop = view.getPaddingTop();
        final int baseRight = view.getPaddingRight();
        final int baseBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout());
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            // Saat papan ketik naik ia sudah menutupi bilah navigasi, jadi
            // keduanya tidak boleh dijumlahkan.
            v.setPadding(
                    baseLeft + bars.left,
                    baseTop,
                    baseRight + bars.right,
                    baseBottom + Math.max(bars.bottom, ime)
            );
            return insets;
        });

        ViewCompat.requestApplyInsets(view);
    }

    /**
     * Gabungan yang dipakai hampir semua layar: siapkan jendela, tumbuhkan
     * bilah atas, dan jauhkan wadah bawah dari bilah navigasi.
     */
    public static void setup(@Nullable Activity activity,
                             @Nullable View topBar,
                             @Nullable View bottomContainer) {
        apply(activity);
        padTopBar(topBar);
        padBottom(bottomContainer);
    }

}
