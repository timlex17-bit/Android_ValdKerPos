package com.valdker.pos.ui.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.valdker.pos.R;

/**
 * Pemberitahuan "sedang luring" berupa popup.
 *
 * <p>Perjalanannya begini: mula-mula sebuah Toast melayang di tengah-bawah
 * layar kasir, menutupi bilah kategori, dan muncul lagi setiap kali data
 * dimuat. Lalu ia menjadi bilah yang menyatu dengan tata letak. Pemilik
 * meminta bentuk ketiga: popup yang harus ditutup sendiri oleh pengguna.
 *
 * <p>Satu hal dari bentuk-bentuk sebelumnya tetap dipertahankan karena itulah
 * inti keluhan yang pertama: kelas ini mengingat keadaan terakhir yang sudah
 * diberitahukan. {@link #setOffline(boolean, CharSequence)} boleh dipanggil
 * setiap kali pemuatan data selesai - berapa pun seringnya - dan popupnya
 * hanya muncul ketika keadaan BERUBAH dari daring menjadi luring. Tanpa
 * ingatan itu, sebuah popup jauh lebih mengganggu daripada toast: ia menuntut
 * ketukan setiap kali muncul.
 *
 * <p>Begitu sambungan kembali, popup yang masih terbuka ditutup sendiri -
 * pengguna tidak perlu menutup pemberitahuan tentang keadaan yang sudah
 * berlalu.
 *
 * <p>Deteksi sambungan dan jalur cadangan ke data lokal tidak ada di sini;
 * kelas ini hanya menerima kesimpulan yang sudah diambil pemanggilnya.
 */
public final class OfflineNotice {

    @Nullable
    private final Context context;
    @Nullable
    private Dialog dialog;
    /** null artinya belum pernah diberitahukan; beda dari "sudah, dan daring". */
    @Nullable
    private Boolean announcedOffline = null;

    private OfflineNotice(@Nullable Context context) {
        this.context = context;
    }

    @NonNull
    public static OfflineNotice attach(@Nullable Context context) {
        return new OfflineNotice(context);
    }

    /**
     * Menyatakan keadaan sambungan saat ini.
     *
     * @param offline true kalau layar ini sedang memakai data lokal
     * @param message kalimat yang ditampilkan saat luring
     */
    public void setOffline(boolean offline, @Nullable CharSequence message) {
        if (announcedOffline != null && announcedOffline == offline) return;
        announcedOffline = offline;

        if (offline) {
            show(message);
        } else {
            dismiss();
        }
    }

    /** Menutup popup yang sedang terbuka, misalnya saat layar ditinggalkan. */
    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        dialog = null;
    }

    private void show(@Nullable CharSequence message) {
        if (!isUsable()) return;
        dismiss();

        CharSequence text = (message == null || message.length() == 0)
                ? context.getString(R.string.offline_banner_message)
                : message;

        dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.offline_notice_title)
                .setIcon(R.drawable.ic_wifi_off)
                .setMessage(text)
                .setPositiveButton(R.string.action_ok, (d, which) -> d.dismiss())
                .show();
    }

    /**
     * Activity yang sudah selesai tidak boleh menampilkan dialog: pemuatan
     * data kerap selesai setelah pengguna meninggalkan layarnya, dan
     * menampilkan dialog pada Activity yang sudah ditutup adalah crash, bukan
     * sekadar pemberitahuan yang terlewat.
     */
    private boolean isUsable() {
        if (context == null) return false;
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            return !activity.isFinishing() && !activity.isDestroyed();
        }
        return true;
    }
}
