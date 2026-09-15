package com.valdker.pos.ui.common;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.valdker.pos.R;

/**
 * Logo toko di bilah atas kasir.
 *
 * <p>Sebelum ini, toko yang belum mengunggah logo mendapat
 * {@code @drawable/bg_logo_circle} - lingkaran ungu polos tanpa isi. Di layar
 * kasir yang setiap harinya dipandangi berjam-jam, lingkaran kosong itu
 * terbaca seperti gambar yang gagal dimuat, bukan seperti keadaan normal. Dan
 * sebagian besar toko memang tidak mengunggah logo: mengunggahnya hanya bisa
 * lewat panel admin, sementara kasirnya dipakai sejak hari pertama.
 *
 * <p>Sekarang lingkaran itu berisi huruf awal nama toko - pola yang sama
 * dipakai aplikasi surel dan pesan untuk kontak tanpa foto. Toko yang punya
 * logo tetap memakai logonya; huruf awal menjadi gambar sementara selama
 * logo diunduh dan gambar pengganti kalau unduhannya gagal, sehingga tidak
 * ada satu keadaan pun yang menampilkan lingkaran kosong.
 *
 * <p>Dipakai bersama kasir restoran (MainActivity) dan kasir retail
 * (RetailPOSFragment) supaya keduanya tidak menyimpang lagi - keduanya dulu
 * menyalin blok Glide yang sama dengan pengganti masing-masing.
 */
public final class ShopAvatar {

    private ShopAvatar() {
    }

    /**
     * Memasang logo toko pada {@code target}.
     *
     * @param shopName nama toko; huruf awalnya dipakai saat tidak ada logo
     * @param logoUrl  URL logo, boleh null atau kosong
     */
    public static void apply(@Nullable ImageView target,
                             @Nullable String shopName,
                             @Nullable String logoUrl) {
        if (target == null) return;

        Drawable fallback = initials(target, shopName);

        if (logoUrl == null || logoUrl.trim().isEmpty()) {
            target.setImageDrawable(fallback);
            return;
        }

        Glide.with(target)
                .load(logoUrl.trim())
                .circleCrop()
                .placeholder(fallback)
                .error(fallback)
                .into(target);
    }

    /** Lingkaran berhuruf awal, tanpa menyentuh ImageView mana pun. */
    @NonNull
    public static Drawable initials(@NonNull ImageView context, @Nullable String shopName) {
        int background = ContextCompat.getColor(context.getContext(), R.color.brand_primary);
        int foreground = ContextCompat.getColor(context.getContext(), R.color.text_on_brand);
        return new InitialDrawable(initialOf(shopName), background, foreground);
    }

    /**
     * Huruf awal pertama yang benar-benar huruf atau angka. Nama seperti
     * "&nbsp;- Toko Ana" tetap menghasilkan "T", bukan tanda hubung.
     */
    private static String initialOf(@Nullable String shopName) {
        if (shopName == null) return "•";
        for (int i = 0; i < shopName.length(); i++) {
            char c = shopName.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                return String.valueOf(Character.toUpperCase(c));
            }
        }
        return "•";
    }

    /**
     * Digambar sendiri alih-alih memakai berkas drawable karena hurufnya
     * berubah per toko, dan ukurannya mengikuti ImageView yang memuatnya -
     * 34dp di lanskap ponsel sampai 44dp di tablet - jadi ukuran hurufnya
     * dihitung dari bidang gambar, bukan dipaku.
     */
    private static final class InitialDrawable extends Drawable {

        private final String text;
        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect textBounds = new Rect();

        InitialDrawable(String text, int background, int foreground) {
            this.text = text;
            circlePaint.setColor(background);
            circlePaint.setStyle(Paint.Style.FILL);
            textPaint.setColor(foreground);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;

            float radius = Math.min(b.width(), b.height()) / 2f;
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), radius, circlePaint);

            textPaint.setTextSize(radius * 0.95f);
            textPaint.getTextBounds(text, 0, text.length(), textBounds);
            // Dipusatkan pada tinggi huruf yang sebenarnya, bukan pada garis
            // dasar: huruf kapital tidak punya bagian bawah, jadi memusatkan
            // lewat baseline membuatnya terlihat terlalu ke bawah.
            float baseline = b.exactCenterY() + textBounds.height() / 2f;
            canvas.drawText(text, b.exactCenterX(), baseline, textPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            circlePaint.setAlpha(alpha);
            textPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            circlePaint.setColorFilter(colorFilter);
            textPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return -1;
        }

        @Override
        public int getIntrinsicHeight() {
            return -1;
        }
    }
}
