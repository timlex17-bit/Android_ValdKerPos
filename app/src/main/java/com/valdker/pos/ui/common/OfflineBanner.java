package com.valdker.pos.ui.common;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.valdker.pos.R;

/**
 * Bilah "luring" di layar kasir, menggantikan Toast yang melayang.
 *
 * <p>Yang salah dengan toast bukan kalimatnya, melainkan sifatnya: ia
 * menutupi isi layar - tepat di atas bilah kategori dan baris kartu menu
 * terbawah, dua hal yang justru ditekan kasir - letaknya tidak tetap, dan ia
 * muncul lagi setiap kali data dimuat ulang walaupun keadaannya tidak
 * berubah. Luring tetap luring; memberitahukannya sepuluh kali tidak menambah
 * satu pun informasi.
 *
 * <p>Kelas ini memegang SATU hal: keadaan terakhir yang sudah ditampilkan.
 * {@link #setOffline(boolean)} boleh dipanggil setiap kali pemuatan data
 * selesai - berapa pun seringnya - dan bilahnya hanya bergerak ketika
 * keadaannya benar-benar berubah. Itulah yang membuat "hanya tampil saat
 * daring -> luring" berlaku tanpa penghitung waktu.
 *
 * <p>Deteksi sambungan dan jalur cadangan ke data lokal TIDAK disentuh sama
 * sekali: kelas ini hanya menerima kesimpulan yang sudah diambil pemanggil.
 */
public final class OfflineBanner {

    private static final long SLIDE_MS = 180L;

    private final View banner;
    /** null artinya belum pernah ditampilkan; beda dari "sudah, dan daring". */
    @Nullable
    private Boolean shownOffline = null;

    private OfflineBanner(@NonNull View banner) {
        this.banner = banner;
    }

    /**
     * Mengikat bilah yang sudah ada di layout.
     *
     * @param root  akar layar; boleh tidak memuat bilahnya sama sekali
     * @param onRetry dijalankan saat tombol "Coba lagi" ditekan; boleh null
     * @return pengelola bilah, atau null kalau layar ini memang tidak punya
     */
    @Nullable
    public static OfflineBanner attach(@Nullable View root, @Nullable Runnable onRetry) {
        if (root == null) return null;

        View banner = root.findViewById(R.id.offlineBanner);
        if (banner == null) return null;

        MaterialButton retry = banner.findViewById(R.id.btnOfflineRetry);
        if (retry != null) {
            if (onRetry == null) {
                retry.setVisibility(View.GONE);
            } else {
                retry.setVisibility(View.VISIBLE);
                retry.setOnClickListener(v -> onRetry.run());
            }
        }

        banner.setVisibility(View.GONE);
        return new OfflineBanner(banner);
    }

    /**
     * Menyatakan keadaan sambungan saat ini. Aman dipanggil berulang: kalau
     * keadaannya sama dengan yang sedang tampil, tidak terjadi apa-apa.
     */
    public void setOffline(boolean offline) {
        if (shownOffline != null && shownOffline == offline) return;
        shownOffline = offline;

        if (offline) {
            slideDown();
        } else {
            slideUp();
        }
    }

    private void slideDown() {
        banner.animate().cancel();
        banner.setVisibility(View.VISIBLE);
        banner.setAlpha(0f);
        banner.setTranslationY(-height());
        banner.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(SLIDE_MS)
                .start();
    }

    private void slideUp() {
        banner.animate().cancel();
        banner.animate()
                .alpha(0f)
                .translationY(-height())
                .setDuration(SLIDE_MS)
                .withEndAction(() -> {
                    banner.setVisibility(View.GONE);
                    banner.setTranslationY(0f);
                    banner.setAlpha(1f);
                })
                .start();
    }

    /**
     * Tinggi bilah untuk jarak geser. Saat pertama kali muncul tingginya masih
     * nol karena belum diukur, jadi dipakai tinggi minimum dari layout -
     * tanpa ini gerakan pertamanya tidak terlihat sama sekali.
     */
    private float height() {
        int measured = banner.getHeight();
        if (measured > 0) return measured;

        ViewGroup.LayoutParams lp = banner.getLayoutParams();
        if (lp != null && lp.height > 0) return lp.height;

        return banner.getResources().getDimension(R.dimen.offline_banner_min_height);
    }
}
