package com.valdker.pos.ui;

import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Menjaga agar ketiga bahasa benar-benar lengkap dan sejalan.
 *
 * <p>Bahasa yang dipakai aplikasi ini ada tiga: Inggris ({@code values/}),
 * Indonesia ({@code values-in/}), dan Tetun ({@code values-b+tet/}).
 *
 * <p>Dua kode kualifikasi itu kerap ditulis salah, dan salahnya tidak terlihat:
 * {@code values-id/} dan {@code values-tet/} ikut dikompilasi dengan baik,
 * hanya saja Android tidak pernah memilihnya. Indonesia di Android memakai kode
 * warisan {@code in} - peninggalan ISO 639 sebelum direvisi - dan Tetun tidak
 * punya kode dua huruf sama sekali, jadi ia wajib ditulis sebagai BCP-47
 * {@code b+tet}. Uji ini ikut menegakkan keduanya, supaya terjemahan yang sudah
 * ditulis tidak berakhir di direktori yang tidak pernah dibaca siapa pun.
 *
 * <p>Tiga hal yang diperiksa:
 *
 * <ol>
 *   <li><b>Kelengkapan.</b> Setiap kunci harus ada di ketiga bahasa. Kunci
 *       yang hilang tidak membuat aplikasi gagal - ia diam-diam jatuh ke
 *       bahasa Inggris, sehingga satu kalimat Inggris muncul di tengah layar
 *       berbahasa Tetun tanpa ada yang melaporkannya.
 *   <li><b>Argumen format.</b> Kalau bahasa Inggris memakai {@code %1$s} dan
 *       terjemahannya lupa memasangnya, hasilnya BUKAN teks yang kurang
 *       lengkap melainkan {@code IllegalFormatException} - aplikasi berhenti,
 *       dan hanya pada perangkat yang bahasanya disetel begitu.
 *   <li><b>Tidak ada kunci yatim.</b> Kunci yang hanya ada di terjemahan
 *       menandakan kunci Inggris yang sudah dihapus tetapi terjemahannya
 *       tertinggal - beban yang tidak pernah terpakai.
 * </ol>
 */
public class TranslationCompletenessTest {

    private static final String BASE = "values";
    private static final String INDONESIAN = "values-in";
    private static final String TETUN = "values-b+tet";

    private static final Pattern STRING =
            Pattern.compile("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", Pattern.DOTALL);

    private static final Pattern FORMAT_ARG = Pattern.compile("%\\d\\$[a-zA-Z]");

    @Test
    public void everyKeyExistsInAllThreeLanguages() throws IOException {
        Map<String, String> english = read(BASE);
        List<String> problems = new ArrayList<>();

        for (String bucket : Arrays.asList(INDONESIAN, TETUN)) {
            Map<String, String> other = read(bucket);

            TreeSet<String> missing = new TreeSet<>(english.keySet());
            missing.removeAll(other.keySet());
            for (String key : missing) {
                problems.add(bucket + ": kunci " + key + " belum diterjemahkan"
                        + " - tanpa itu teks Inggris muncul di tengah layar"
                        + " berbahasa lain, tanpa galat apa pun");
            }

            TreeSet<String> orphan = new TreeSet<>(other.keySet());
            orphan.removeAll(english.keySet());
            for (String key : orphan) {
                problems.add(bucket + ": kunci " + key + " tidak punya padanan"
                        + " di values/ - kemungkinan sisa kunci yang sudah dihapus");
            }
        }

        if (!problems.isEmpty()) {
            fail("Terjemahan belum lengkap:\n  " + String.join("\n  ", problems));
        }
    }

    @Test
    public void formatArgumentsMatchAcrossLanguages() throws IOException {
        Map<String, String> english = read(BASE);
        List<String> problems = new ArrayList<>();

        for (String bucket : Arrays.asList(INDONESIAN, TETUN)) {
            Map<String, String> other = read(bucket);
            for (Map.Entry<String, String> entry : english.entrySet()) {
                String translated = other.get(entry.getKey());
                if (translated == null) continue;

                TreeSet<String> here = argumentsOf(entry.getValue());
                TreeSet<String> there = argumentsOf(translated);
                if (!here.equals(there)) {
                    problems.add(bucket + " / " + entry.getKey()
                            + ": Inggris memakai " + here + ", terjemahannya " + there
                            + " - selisih ini melempar IllegalFormatException saat"
                            + " berjalan, hanya pada perangkat berbahasa itu");
                }
            }
        }

        if (!problems.isEmpty()) {
            fail("Argumen format tidak sejalan:\n  " + String.join("\n  ", problems));
        }
    }

    @Test
    public void localeQualifiersUseTheCodesAndroidActuallyResolves() {
        List<String> problems = new ArrayList<>();
        File res = resolve("src/main/res");

        if (!new File(res, INDONESIAN).isDirectory()) {
            problems.add("values-in/ tidak ada - Indonesia di Android memakai kode"
                    + " warisan 'in', bukan 'id'");
        }
        if (!new File(res, TETUN).isDirectory()) {
            problems.add("values-b+tet/ tidak ada - Tetun tidak punya kode dua huruf,"
                    + " jadi wajib ditulis sebagai BCP-47 b+tet");
        }
        if (new File(res, "values-id").isDirectory()) {
            problems.add("values-id/ ada - direktori ini tidak pernah dipilih Android;"
                    + " isinya harus pindah ke values-in/");
        }
        if (new File(res, "values-tet").isDirectory()) {
            problems.add("values-tet/ ada - direktori ini tidak pernah dipilih Android;"
                    + " isinya harus pindah ke values-b+tet/");
        }

        if (!problems.isEmpty()) {
            fail("Kualifikasi bahasa salah:\n  " + String.join("\n  ", problems));
        }
    }

    private TreeSet<String> argumentsOf(String value) {
        TreeSet<String> out = new TreeSet<>();
        Matcher m = FORMAT_ARG.matcher(value);
        while (m.find()) out.add(m.group());
        return out;
    }

    private Map<String, String> read(String bucket) throws IOException {
        File file = new File(resolve("src/main/res/" + bucket), "strings.xml");
        Map<String, String> out = new LinkedHashMap<>();
        String src = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Matcher m = STRING.matcher(src);
        while (m.find()) out.put(m.group(1), m.group(2));
        return out;
    }

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
