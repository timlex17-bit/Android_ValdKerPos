package com.valdker.pos.print;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;

/**
 * Menyusun satu baris struk menjadi teks yang siap dikirim ke printer.
 *
 * <p>Kelas ini yang membuat {@code [R]} benar-benar rata kanan. Sebelumnya
 * encoder hanya membaca tag di awal baris lalu mengganti setiap {@code [R]}
 * di tengah baris dengan SATU SPASI, jadi seluruh struk tidak punya kolom:
 * yang tercetak "Subtotal $10.00", bukan "Subtotal" di kiri dan "$10.00"
 * mepet ke tepi kanan kertas. Semua angka pada struk melewati jalur ini, jadi
 * tidak ada satu pun yang sejajar.
 *
 * <p>Aturannya sekarang: baris dipecah pada {@code [R]} pertama, sisa kolom
 * dihitung dari lebar kertas ({@link PrinterService#profileForMm}, 58mm = 32
 * kolom dan 80mm = 48 kolom), lalu spasi disisipkan di antaranya. Kalau kiri
 * dan kanan bersama-sama melebihi lebar kertas, yang dipotong adalah bagian
 * KIRI - nama produk boleh terpotong, angka tidak pernah.
 *
 * <p>Ada juga token {@link #SEPARATOR}: garis pemisah dulu ditulis sebagai 32
 * setrip mati di lima berkas berbeda, yang berarti benar hanya untuk kertas
 * 58mm dan jadi garis pendek yang menggantung di kertas 80mm. Pembuat struk
 * kini menulis tokennya saja dan lebar sesungguhnya diisi di sini.
 *
 * <p>Sengaja Java biasa tanpa ketergantungan Android supaya bisa diuji di
 * JVM: perataan kolom adalah hal yang paling mudah salah dan paling mahal
 * diverifikasi dengan printer sungguhan.
 */
public final class ReceiptLayout {

    /** Baris yang berisi token ini diganti garis selebar kertas. */
    public static final String SEPARATOR = "[SEP]";

    public static final byte ALIGN_LEFT = 0;
    public static final byte ALIGN_CENTER = 1;
    public static final byte ALIGN_RIGHT = 2;

    /** Lebar terkecil yang masuk akal untuk printer struk. */
    private static final int MIN_WIDTH = 24;

    private ReceiptLayout() {
    }

    /** Satu baris yang sudah siap cetak. */
    public static final class Line {
        public final byte align;
        public final boolean bold;
        @NonNull
        public final String text;

        Line(byte align, boolean bold, @NonNull String text) {
            this.align = align;
            this.bold = bold;
            this.text = text;
        }
    }

    /**
     * Mengubah satu baris bertag menjadi teks siap cetak pada lebar tertentu.
     *
     * @param raw   baris seperti {@code "[L]Subtotal[R]$10.00"}
     * @param width jumlah kolom kertas (32 untuk 58mm, 48 untuk 80mm)
     */
    @NonNull
    public static Line layout(@Nullable String raw, int width) {
        int cols = Math.max(MIN_WIDTH, width);
        String text = raw == null ? "" : raw;

        boolean bold = text.contains("<b>") || text.contains("</b>");

        byte align = ALIGN_LEFT;
        if (text.startsWith("[C]")) {
            align = ALIGN_CENTER;
            text = text.substring(3);
        } else if (text.startsWith("[R]")) {
            align = ALIGN_RIGHT;
            text = text.substring(3);
        } else if (text.startsWith("[L]")) {
            text = text.substring(3);
        }

        text = stripBold(text);

        if (SEPARATOR.equals(text.trim())) {
            return new Line(align, bold, repeat('-', cols));
        }

        int split = text.indexOf("[R]");
        if (split < 0) {
            return new Line(align, bold, wrap(sanitize(clearTags(text)), cols));
        }

        // Dua kolom dalam satu baris. Perataannya dihitung di sini, jadi
        // printer harus diminta rata kiri - kalau tidak, ia akan menggeser
        // lagi baris yang sudah pas selebar kertas.
        String left = sanitize(clearTags(text.substring(0, split)));
        String right = sanitize(clearTags(text.substring(split + 3)));

        return new Line(ALIGN_LEFT, bold, justify(left, right, cols));
    }

    /** Garis pemisah selebar kertas. */
    @NonNull
    public static String separator(int width) {
        return repeat('-', Math.max(MIN_WIDTH, width));
    }

    /**
     * Menempatkan {@code right} mepet tepi kanan dan {@code left} di sisa
     * ruang sebelahnya.
     *
     * <p>Angka tidak pernah dipotong. Kalau bagian kanan sendiri sudah
     * selebar kertas atau lebih, ia dikembalikan apa adanya dan bagian kiri
     * yang mengalah sepenuhnya - struk yang kehilangan nama produk masih bisa
     * dibaca, struk yang kehilangan digit tidak.
     */
    @NonNull
    static String justify(@NonNull String left, @NonNull String right, int width) {
        if (right.isEmpty()) return left;
        if (right.length() >= width) return right;

        if (left.isEmpty()) {
            return repeat(' ', width - right.length()) + right;
        }

        // Sisakan minimal satu spasi supaya nama dan angka tidak menempel.
        int roomForLeft = width - right.length() - 1;
        String fittedLeft = left.length() > roomForLeft
                ? left.substring(0, Math.max(0, roomForLeft))
                : left;

        int gap = width - fittedLeft.length() - right.length();
        return fittedLeft + repeat(' ', Math.max(1, gap)) + right;
    }

    /**
     * Memotong baris yang lebih panjang dari lebar kertas menjadi beberapa
     * baris. Hanya dipakai untuk baris tanpa kolom kanan; baris berkolom
     * sudah pas selebar kertas.
     */
    @NonNull
    private static String wrap(@NonNull String text, int width) {
        if (text.length() <= width) return text;

        StringBuilder sb = new StringBuilder();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + width, text.length());
            sb.append(text, start, end);
            start = end;
            if (start < text.length()) sb.append('\n');
        }
        return sb.toString();
    }

    @NonNull
    private static String stripBold(@NonNull String text) {
        return text.replace("<b>", "").replace("</b>", "");
    }

    /** Membuang tag yang tersisa di tengah baris. */
    @NonNull
    private static String clearTags(@NonNull String text) {
        return text.replace("[L]", "").replace("[C]", "").replace("[R]", "");
    }

    @NonNull
    private static String repeat(char c, int count) {
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) sb.append(c);
        return sb.toString();
    }

    /**
     * Membuang diakritik dan menormalkan tanda baca yang tidak dimiliki
     * printer. Dijalankan sebelum perataan karena panjang teks bisa berubah -
     * huruf beraksen menjadi huruf polos, dan kolom harus dihitung dari panjang yang
     * benar-benar dicetak.
     */
    @NonNull
    static String sanitize(@Nullable String value) {
        if (value == null) return "";
        String clean = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Ditulis sebagai escape unicode, bukan karakternya langsung:
        // berkas sumber dibaca javac dengan encoding platform, dan pada
        // mesin Windows karakter literal di sini berubah jadi byte lain -
        // penggantinya lalu tidak pernah cocok dan simbolnya lolos ke
        // printer sebagai tanda tanya.
        return clean.replace("\u2705", "*")
                .replace("\u2014", "-")
                .replace("\u2013", "-")
                .replace("\u2022", "-")
                .replace("\u00e2\u20ac\u201d", "-")
                .replace("\u00e2\u20ac\u00a2", "-")
                .replace("\u00e2\u2013\u00a0", "*")
                .replace("\u00e2\u2013\u00b2", "^")
                .trim();
    }
}
