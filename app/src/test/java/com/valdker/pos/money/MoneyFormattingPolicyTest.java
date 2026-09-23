package com.valdker.pos.money;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Menjaga agar seluruh aplikasi menampilkan uang dalam satu bentuk: {@code $0.00}.
 *
 * <p>{@link MoneyTest} sudah menguji bahwa {@link Money#format()} benar. Yang
 * tidak bisa diuji dari sana adalah tempat-tempat yang TIDAK memanggilnya sama
 * sekali dan menyusun sendiri teks nominalnya. Itulah yang membuat satu layar
 * menampilkan "$0.00" sementara layar sebelahnya menampilkan "$ 0.00" - beda
 * satu spasi, cukup untuk membuat deretan angka terlihat tidak sejajar tanpa
 * ada yang bisa menunjuk sebabnya - dan yang membuat sebuah saldo tergambar
 * sebagai "$1,750" tanpa sen.
 *
 * <p>Uji ini membaca sumbernya, bukan menjalankan aplikasinya, karena cacat
 * semacam ini tidak muncul sebagai perilaku yang salah: ia muncul sebagai
 * angka yang benar dalam bentuk yang salah, dan tidak ada assertion perilaku
 * yang bisa menangkapnya.
 *
 * <p>Dua aturannya:
 *
 * <ol>
 *   <li>Di layout, {@code android:text} tidak boleh memuat nominal tertulis.
 *       Nilai nolnya memakai {@code @string/amount_zero_usd}; selebihnya diisi
 *       kode lewat {@code Money.format()}. Teks contoh untuk pratinjau tetap
 *       bebas, karena tempatnya {@code tools:text} dan itu tidak pernah sampai
 *       ke perangkat.
 *   <li>Di kode, nominal tidak boleh ditulis sebagai literal. Pengecualiannya
 *       hanya berkas yang memang MENDEFINISIKAN atau MENDOKUMENTASIKAN
 *       bentuknya.
 * </ol>
 */
public class MoneyFormattingPolicyTest {

    /**
     * Berkas yang boleh memuat nominal tertulis, berikut alasannya.
     *
     * <p>Bukan daftar pengecualian yang boleh tumbuh: setiap tambahan di sini
     * berarti satu tempat lagi yang bentuk uangnya tidak dijamin siapa pun.
     */
    private static final List<String> ALLOWED = Arrays.asList(
            // Mendefinisikan bentuknya, jadi harus menyebutnya.
            "money/Money.java"
    );

    /** Nominal tertulis: $0.00, $ 0.00, $1,750, $10 - berikut variasinya. */
    private static final Pattern MONEY_LITERAL =
            Pattern.compile("\\$\\s*\\d[\\d,]*(\\.\\d+)?");

    @Test
    public void formatterProducesTheAgreedShape() {
        assertEquals("$0.00", Money.zero().format());
        assertEquals("$0.00", Money.of("0").format());
        assertEquals("$1.00", Money.of("1").format());
        assertEquals("$1.50", Money.of("1.5").format());
        assertEquals("$10.50", Money.of("10.5").format());
        assertEquals("$100.00", Money.of("100").format());
        assertEquals("$1,000.00", Money.of("1000").format());
        assertEquals("$1,250.50", Money.of("1250.5").format());
        assertEquals("$10,000.00", Money.of("10000").format());
        assertEquals("$1,000,000.00", Money.of("1000000").format());
    }

    /**
     * Nilai yang dikirim ke server tetap desimal polos.
     *
     * <p>Pemisah ribuan dan tanda dolar hanya untuk mata manusia. Kalau salah
     * satu ikut terkirim, server menerima "$1,250.50" untuk sebuah kolom
     * Decimal - dan yang gagal bukan tampilannya, melainkan transaksinya.
     */
    @Test
    public void apiShapeStaysPlainDecimal() {
        assertEquals("0.00", Money.zero().toPlainString());
        assertEquals("1.50", Money.of("1.5").toPlainString());
        assertEquals("1250.50", Money.of("1250.5").toPlainString());
        assertEquals("1000000.00", Money.of("1000000").toPlainString());
    }

    /**
     * Bentuk tampilan dan bentuk payload tidak boleh tertukar.
     *
     * <p>Keduanya berasal dari nilai yang sama, jadi perbedaannya harus datang
     * dari metode yang dipanggil - bukan dari kebiasaan pemanggilnya.
     */
    @Test
    public void displayAndPayloadNeverProduceTheSameString() {
        for (String raw : new String[]{"0", "1", "1.5", "10.5", "1000", "1250.5",
                "10000", "1000000"}) {
            Money value = Money.of(raw);
            if (value.format().equals(value.toPlainString())) {
                fail("format() dan toPlainString() menghasilkan teks yang sama untuk "
                        + raw + " - tanda dolarnya hilang dari salah satu");
            }
        }
    }

    @Test
    public void noLayoutWritesAnAmountByHand() throws IOException {
        List<String> problems = new ArrayList<>();
        for (File file : layoutFiles()) {
            String src = read(file);
            Matcher m = Pattern.compile("android:(?:text|hint)\\s*=\\s*\"([^\"]*)\"")
                    .matcher(src);
            while (m.find()) {
                String value = m.group(1);
                if (value.startsWith("@")) continue;
                if (MONEY_LITERAL.matcher(value).find()) {
                    problems.add(file.getParentFile().getName() + "/" + file.getName()
                            + ": android:text=\"" + value + "\""
                            + " - nominal nol memakai @string/amount_zero_usd,"
                            + " selebihnya diisi kode lewat Money.format();"
                            + " teks contoh untuk pratinjau memakai tools:text");
                }
            }
        }
        if (!problems.isEmpty()) {
            fail("Nominal ditulis tangan di layout:\n  " + String.join("\n  ", problems));
        }
    }

    @Test
    public void noCodeWritesAnAmountByHand() throws IOException {
        List<String> problems = new ArrayList<>();
        for (File file : javaFiles(resolve("src/main/java"))) {
            String path = file.getPath().replace('\\', '/');
            boolean allowed = false;
            for (String suffix : ALLOWED) {
                if (path.endsWith(suffix)) {
                    allowed = true;
                    break;
                }
            }
            if (allowed) continue;

            Matcher m = Pattern.compile("\"([^\"\\n]*)\"")
                    .matcher(stripComments(read(file)));
            while (m.find()) {
                String value = m.group(1);
                if (MONEY_LITERAL.matcher(value).find()) {
                    problems.add(file.getName() + ": \"" + value + "\""
                            + " - pakai Money.format(), supaya bentuknya tidak bercabang");
                }
            }
        }
        if (!problems.isEmpty()) {
            fail("Nominal ditulis tangan di kode:\n  " + String.join("\n  ", problems));
        }
    }

    /**
     * Tanda dolar tidak boleh dirangkai dengan nilai apa pun.
     *
     * <p>Aturan "jangan tulis nominal sebagai literal" tidak menangkap bentuk
     * ini: {@code "$" + amount} tidak memuat satu pun angka, jadi ia lolos -
     * padahal hasilnya persis cacat yang sama. Sebuah baris pengeluaran
     * tergambar "$1250.5" dengan satu desimal dan tanpa pemisah ribuan, dan
     * tidak ada yang bisa menunjuk sebabnya karena nilainya memang benar.
     *
     * <p>Simbolnya milik {@link Money#format()}. Kalau sebuah layar perlu
     * menampilkan uang, ia memanggil format() - bukan menempelkan tanda dolar
     * di depan apa pun yang kebetulan ada di tangannya.
     */
    @Test
    public void noCodeGluesADollarSignToAValue() throws IOException {
        Pattern glued = Pattern.compile("\"\\$\"\\s*\\+|\\+\\s*\"\\$\"");
        List<String> problems = new ArrayList<>();

        for (File file : javaFiles(resolve("src/main/java"))) {
            if (file.getPath().replace('\\', '/').endsWith("money/Money.java")) continue;
            Matcher m = glued.matcher(stripComments(read(file)));
            while (m.find()) {
                problems.add(file.getName() + ": " + m.group()
                        + " - simbolnya milik Money.format(), bukan ditempel"
                        + " di depan nilai mentah");
            }
        }

        if (!problems.isEmpty()) {
            fail("Tanda dolar dirangkai dengan nilai:\n  " + String.join("\n  ", problems));
        }
    }

    private List<File> layoutFiles() {
        List<File> out = new ArrayList<>();
        File res = resolve("src/main/res");
        File[] dirs = res.listFiles();
        if (dirs == null) return out;
        for (File dir : dirs) {
            if (!dir.isDirectory() || !dir.getName().startsWith("layout")) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (f.getName().endsWith(".xml")) out.add(f);
            }
        }
        return out;
    }

    private List<File> javaFiles(File dir) {
        List<File> out = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) return out;
        for (File f : children) {
            if (f.isDirectory()) out.addAll(javaFiles(f));
            else if (f.getName().endsWith(".java")) out.add(f);
        }
        return out;
    }

    /**
     * Membuang komentar sebelum mencari literal.
     *
     * <p>Komentar bukan kode. Tanpa ini, kalimat yang MENJELASKAN bentuk uang
     * yang salah - "sementara layar lain menampilkan $0.00" - terbaca sebagai
     * pelanggaran, sehingga satu-satunya cara membuat uji ini hijau adalah
     * berhenti menulis penjelasan. Uji yang menghukum penjelasan akan
     * menghasilkan kode tanpa penjelasan.
     */
    private String stripComments(String src) {
        return src
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//[^\\n]*", " ");
    }

    private String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * Direktori kerja uji JVM berbeda antara Gradle dan IDE - kadang modul
     * {@code app/}, kadang akar repositori.
     */
    private File resolve(String relative) {
        File dir = new File("").getAbsoluteFile();
        for (int i = 0; i < 4 && dir != null; i++, dir = dir.getParentFile()) {
            File direct = new File(dir, relative);
            if (direct.isDirectory()) return direct;
            File inApp = new File(new File(dir, "app"), relative);
            if (inApp.isDirectory()) return inApp;
        }
        throw new IllegalStateException("Tidak menemukan " + relative);
    }
}
