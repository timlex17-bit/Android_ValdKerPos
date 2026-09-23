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
            "money/Money.java",
            // Menjelaskan bentuk baris struk di dalam dokumentasinya.
            "print/ReceiptContent.java",
            "print/ReceiptLayout.java",
            "print/ReceiptBuilder.java",
            // Menceritakan cacat yang sudah diperbaiki di dalam komentar.
            "MainActivity.java",
            "cart/CartManager.java",
            "ui/reports/ReportsFragment.java"
    );

    /** Nominal tertulis: $0.00, $ 0.00, $1,750, $10 - berikut variasinya. */
    private static final Pattern MONEY_LITERAL =
            Pattern.compile("\\$\\s*\\d[\\d,]*(\\.\\d+)?");

    @Test
    public void formatterProducesTheAgreedShape() {
        assertEquals("$0.00", Money.zero().format());
        assertEquals("$1.00", Money.of("1").format());
        assertEquals("$10.50", Money.of("10.5").format());
        assertEquals("$1,000.00", Money.of("1000").format());
        assertEquals("$1,250.50", Money.of("1250.50").format());
        assertEquals("$10,000.00", Money.of("10000").format());
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

            Matcher m = Pattern.compile("\"([^\"\\n]*)\"").matcher(read(file));
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
