package com.valdker.pos.ui.common;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.R;

/**
 * Mengunci layar pada posisi tegak di ponsel, dan membiarkannya bebas
 * berputar di tablet.
 *
 * <p>Alasannya ada di {@code values/bools.xml}: aplikasi kasir dipegang tegak
 * dengan satu tangan, dan putaran yang tidak disengaja - perangkat dimiringkan
 * saat diletakkan atau diserahkan - membangun ulang seluruh layar di tengah
 * transaksi. Tablet kasir justru kerap dipasang mendatar pada dudukan, dan
 * susunan dua kolomnya memang ditulis untuk itu.
 *
 * <p>MENGAPA DI SINI, BUKAN DI MANIFES
 *
 * <p>{@code android:screenOrientation="portrait"} di manifes akan mengunci
 * SEMUA perangkat, tablet sekalipun. Membuatnya bergantung ukuran layar lewat
 * rujukan sumber daya - {@code android:screenOrientation="@integer/..."} -
 * memang bisa ditulis, tetapi manifes dibaca PackageManager dengan konfigurasi
 * yang bukan konfigurasi perangkat saat itu, sehingga kelompok seperti
 * {@code values-sw600dp/} tidak dijamin ikut dipilih. Google sendiri
 * menyarankan agar nilai di manifes tidak bergantung pada konfigurasi.
 *
 * <p>MENGAPA SATU TEMPAT, BUKAN DI TIAP ACTIVITY
 *
 * <p>Aplikasi ini punya dua puluh satu Activity. Menyalin satu baris ke dalam
 * {@code onCreate} masing-masing berarti dua puluh satu tempat yang bisa
 * terlewat, dan Activity yang ditambahkan besok akan terlewat dengan
 * sendirinya - tanpa galat, hanya satu layar yang diam-diam ikut berputar
 * sementara yang lain tidak. Dipasang sekali di {@link Application} lewat
 * {@link Application.ActivityLifecycleCallbacks}, aturannya berlaku untuk
 * setiap Activity yang sudah ada maupun yang belum ditulis.
 *
 * <p>CATATAN TENTANG BATASNYA
 *
 * <p>Kunci ini bukan jaminan mutlak, dan memang tidak boleh diperlakukan
 * begitu. Dalam mode jendela terbagi Android mengabaikan permintaan orientasi;
 * pada Android 16 permintaan orientasi juga diabaikan untuk layar besar; dan
 * ponsel lipat yang dibuka berpindah ke kelompok tablet. Karena itu tata letak
 * mendatar untuk ponsel - {@code layout-land/} dan {@code values-land/} -
 * sengaja TIDAK dihapus: ia tetap jalur yang dipakai pada keadaan-keadaan itu,
 * dan menghapusnya hanya akan memindahkan cacatnya ke tempat yang lebih sulit
 * ditemukan.
 */
public final class ScreenOrientation {

    private ScreenOrientation() {
    }

    /**
     * Memasang aturan orientasi untuk seluruh Activity aplikasi.
     *
     * <p>Dipanggil sekali dari {@code Application.onCreate()}.
     */
    public static void install(@NonNull Application application) {
        application.registerActivityLifecycleCallbacks(new Callbacks());
    }

    /**
     * Menerapkan aturan pada satu Activity.
     *
     * <p>Di tablet tidak ada yang disetel sama sekali - bukan disetel ke
     * "bebas". Bedanya penting: menyetel orientasi, bahkan ke
     * {@code UNSPECIFIED}, dapat memicu Activity dibangun ulang bila posisi
     * perangkat saat itu berbeda, dan itu berarti tablet mendatar berkedip
     * sekali setiap membuka layar - persis hal yang hendak dihindari.
     */
    public static void apply(@Nullable Activity activity) {
        if (activity == null) return;
        if (activity.getResources().getBoolean(R.bool.pos_allow_rotation)) return;

        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private static final class Callbacks implements Application.ActivityLifecycleCallbacks {

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) {
            // Dikirim dari dalam super.onCreate(), jadi sebelum setContentView
            // milik Activity-nya berjalan: kalau orientasinya memang perlu
            // diubah, pembangunan ulangnya terjadi sebelum ada yang tergambar.
            apply(activity);
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle out) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
    }
}
