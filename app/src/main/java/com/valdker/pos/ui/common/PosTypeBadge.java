package com.valdker.pos.ui.common;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.R;
import com.valdker.pos.SessionManager;

import java.util.Locale;

/**
 * Lencana jenis usaha terkunci di kepala layar kasir, dan tiga label strip
 * aksi di bawahnya.
 *
 * <p>Satu login terikat pada satu jenis usaha; tidak ada jalan berpindah dari
 * layar kasir. Karena itu tipe ditampilkan sebagai lencana bergembok - bukan
 * tab, bukan tombol, bukan chip. Bedanya bukan kosmetik: kontrol yang terlihat
 * bisa ditekan akan dicoba, dan kasir yang menekannya lalu tidak terjadi apa-apa
 * akan menyimpulkan aplikasinya rusak.
 *
 * <p>Isinya datang dari sesi, bukan dari layout, karena hanya sesi yang tahu
 * tipe akun yang sedang masuk - dan itu pula yang membuat lencana ini jujur:
 * ia menampilkan apa yang benar-benar menentukan perilaku server, bukan apa
 * yang kebetulan ditulis di berkas XML mana.
 *
 * <p>Kelas ini dulu juga mengisi tiga label strip aksi cepat di ketiga kasir.
 * Strip itu sudah dihapus seluruhnya - tidak satu pun selnya pernah punya
 * listener, jadi ketiganya hanya gambar yang bisa ditekan tanpa akibat - dan
 * bersamanya {@code applyQuickStrip} ikut hilang dari sini.
 *
 * <p>Yang tersisa hanya lencana di LAYAR MASUK. Di layar kasir lencananya juga
 * sudah dilepas: mengulang jenis usaha di setiap layar transaksi tidak
 * menambah apa pun, karena tidak ada jalan mengubahnya dari sana.
 */
public final class PosTypeBadge {

    private PosTypeBadge() {
    }

    /**
     * Memasang lencana tipe pada {@code root}, bila layout-nya memuatnya.
     *
     * <p>Aman dipanggil dari layar mana pun: tata letak yang tidak memuat
     * lencana (mis. varian tablet lama) hanya dilewati.
     */
    public static void apply(@Nullable View root, @Nullable SessionManager session) {
        if (root == null) return;

        TextView badge = root.findViewById(R.id.tvPosTypeBadge);
        if (badge == null) return;

        int labelRes = labelResFor(session);
        badge.setText(labelRes);
        badge.setVisibility(View.VISIBLE);

        // Bukan tombol. Dinyatakan eksplisit supaya pembaca layar juga
        // menyebutnya sebagai teks, bukan sebagai kontrol.
        badge.setClickable(false);
        badge.setFocusable(false);
    }

    public static int labelResFor(@Nullable SessionManager session) {
        switch (typeOf(session)) {
            case "restaurant":
                return R.string.pos_type_restaurant;
            case "workshop":
                return R.string.pos_type_workshop;
            default:
                return R.string.pos_type_retail;
        }
    }

    /**
     * Jenis usaha yang sudah dibakukan.
     *
     * <p>Server mengenal {@code general_workshop} di samping {@code workshop},
     * dan keduanya adalah bengkel bagi kasir.
     */
    @NonNull
    private static String typeOf(@Nullable SessionManager session) {
        if (session == null) return "retail";

        String raw = session.getShopBusinessType();
        String type = raw == null ? "" : raw.trim().toLowerCase(Locale.US);

        if (type.startsWith("restaurant")) return "restaurant";
        if (type.startsWith("workshop") || type.startsWith("general_workshop")) return "workshop";
        return "retail";
    }
}
