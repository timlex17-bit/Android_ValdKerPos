package com.valdker.pos.ui;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Menjaga agar tablet yang sedang mendatar tidak ikut memakai ukuran kasir
 * yang dipendekkan untuk ponsel.
 *
 * <p>Perangkapnya halus dan tidak menimbulkan galat apa pun. Urutan pemilihan
 * sumber daya Android memang menaruh <em>smallest-width</em> di atas
 * <em>orientation</em>, sehingga mudah disimpulkan bahwa {@code values-sw600dp/}
 * selalu mengalahkan {@code values-land/} pada tablet. Yang benar: urutan itu
 * hanya memilih di antara kelompok yang BENAR-BENAR MEMUAT nama yang diminta.
 * Kalau sebuah nama hanya ada di {@code values/} dan {@code values-land/}, maka
 * tablet 10 inci yang dipegang mendatar akan mengambil nilai
 * {@code values-land/} - yaitu nilai yang ditulis untuk layar setinggi 360dp -
 * meskipun layarnya setinggi 800dp.
 *
 * <p>Akibatnya kasir tablet tampil dengan kepala pendek, tombol bayar pendek,
 * dan angka total yang mengecil tanpa alasan, dan tidak ada satu pun galat
 * kompilasi, peringatan lint, atau kegagalan uji yang menunjukkannya.
 *
 * <p>Karena itu aturannya: setiap {@code pos_*} yang dipendekkan di
 * {@code values-land/dimens_pos.xml} wajib punya pasangan di
 * {@code values-sw600dp/dimens_pos.xml}, dan nilai pasangan itu harus sama
 * dengan nilai baku di {@code values/dimens_pos.xml}. Kalau suatu hari tablet
 * memang perlu ukuran tersendiri, ubah keduanya - uji ini akan menunjukkan
 * tepat nama mana yang belum ikut diubah.
 */
public class PosLandscapeTokenParityTest {

    private static final Pattern DIMEN =
            Pattern.compile("<dimen name=\"([^\"]+)\">([^<]+)</dimen>");

    @Test
    public void everyLandscapePosTokenHasATabletCounterpart() throws IOException {
        Map<String, String> base = read("values");
        Map<String, String> land = read("values-land");
        Map<String, String> tablet = read("values-sw600dp");

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> entry : land.entrySet()) {
            String name = entry.getKey();
            if (!base.containsKey(name)) {
                problems.add(name + ": ada di values-land/ tapi tidak punya nilai baku"
                        + " di values/ - nilai bakunya jadi tidak terdefinisi"
                        + " pada ponsel yang tegak");
                continue;
            }
            if (!tablet.containsKey(name)) {
                problems.add(name + ": dipendekkan jadi " + entry.getValue()
                        + " di values-land/ tapi tidak ada di values-sw600dp/"
                        + " - tablet mendatar akan ikut memakai nilai pendek itu");
                continue;
            }
            if (!tablet.get(name).equals(base.get(name))) {
                problems.add(name + ": values-sw600dp/ berisi " + tablet.get(name)
                        + " sedangkan values/ berisi " + base.get(name)
                        + " - kalau perbedaan ini disengaja, perbarui uji ini"
                        + " berikut alasannya");
            }
        }
        if (!problems.isEmpty()) {
            fail("Ukuran kasir lanskap belum berpasangan:\n  "
                    + String.join("\n  ", problems));
        }
        assertTrue("values-land/dimens_pos.xml kosong - apakah berkasnya pindah?",
                !land.isEmpty());
    }

    private Map<String, String> read(String bucket) throws IOException {
        File file = resolve("src/main/res/" + bucket + "/dimens_pos.xml");
        Map<String, String> out = new LinkedHashMap<>();
        Matcher matcher = DIMEN.matcher(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        while (matcher.find()) {
            out.put(matcher.group(1), matcher.group(2).trim());
        }
        return out;
    }

    /**
     * Direktori kerja uji JVM berbeda antara Gradle dan IDE - kadang modul
     * {@code app/}, kadang akar repositori. Dicari naik sampai ketemu, supaya
     * uji ini tidak gagal hanya karena cara menjalankannya berbeda.
     */
    private File resolve(String relative) {
        File dir = new File("").getAbsoluteFile();
        for (int i = 0; i < 4 && dir != null; i++, dir = dir.getParentFile()) {
            File direct = new File(dir, relative);
            if (direct.isFile()) return direct;
            File inApp = new File(new File(dir, "app"), relative);
            if (inApp.isFile()) return inApp;
        }
        throw new IllegalStateException("Tidak menemukan " + relative
                + " dari " + new File("").getAbsolutePath());
    }
}
