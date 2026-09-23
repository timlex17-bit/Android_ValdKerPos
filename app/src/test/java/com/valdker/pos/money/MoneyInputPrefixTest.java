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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Menjaga agar setiap kolom tempat pengguna MENGETIK uang menunjukkan "$".
 *
 * <h3>Kenapa uji terpisah dari MoneyFormattingPolicyTest</h3>
 *
 * <p>Keduanya tentang mata uang, tetapi menjaga hal yang berbeda, dan
 * perbedaan itu pernah membuat audit sebelumnya menyatakan "mata uang selesai"
 * padahal separuhnya belum tersentuh. Yang satu menjaga TAMPILAN - bahwa nilai
 * yang sudah jadi tergambar sebagai {@code $1,250.50}. Yang ini menjaga
 * ISIAN - bahwa kolom kosong tempat kasir akan mengetik angka sudah menyatakan
 * mata uangnya sebelum satu karakter pun diketik.
 *
 * <p>Sebuah kolom harga yang menampilkan hint "0.00" lolos seluruh pemeriksaan
 * tampilan dengan mulus: tidak ada nominal yang salah bentuk di sana, karena
 * belum ada nominal sama sekali. Yang hilang adalah keterangannya.
 *
 * <h3>Dua mekanisme, satu syarat</h3>
 *
 * <p>Kolom di dalam {@code TextInputLayout} memakai {@code app:prefixText};
 * {@code EditText} polos dibungkus kotak uang dengan "$" sebagai TextView.
 * Keduanya diterima uji ini, karena yang penting bukan caranya melainkan
 * sifatnya: pada keduanya "$" berada DI LUAR teks yang diedit, sehingga
 * {@code getText()} tidak pernah mengembalikannya dan payload API tetap bersih.
 */
public class MoneyInputPrefixTest {

    /**
     * Setiap kolom tempat pengguna mengetik nominal, berikut berkasnya.
     *
     * <p>Ditulis tangan dan memang harus begitu: tidak ada cara mengenali
     * "kolom ini memuat uang" dari XML saja. {@code etQuantity} dan
     * {@code etAmount} sama-sama {@code numberDecimal}; yang membedakan cuma
     * artinya. Daftar inilah keputusan itu, tertulis di satu tempat.
     */
    private static final Map<String, String> MONEY_INPUTS = new LinkedHashMap<>();

    static {
        MONEY_INPUTS.put("etOpeningBalance", "dialog_bank_account.xml");
        MONEY_INPUTS.put("etClosingCash", "dialog_close_shift.xml");
        MONEY_INPUTS.put("etAmount", "dialog_expense_form.xml");
        MONEY_INPUTS.put("etPrice", "dialog_service_package_form.xml");
        MONEY_INPUTS.put("etOpeningCash", "dialog_shift_open.xml");
        MONEY_INPUTS.put("etCashReceived", "dialog_native_checkout.xml");
        MONEY_INPUTS.put("etDeliveryFee", "dialog_native_checkout.xml");
        MONEY_INPUTS.put("etUnitPrice", "dialog_product_return_add.xml");
        MONEY_INPUTS.put("etManualUnitPrice", "dialog_product_return_add.xml");
        MONEY_INPUTS.put("etCost", "dialog_purchase_add.xml");
        MONEY_INPUTS.put("etSplitAmount", "item_split_payment.xml");
        MONEY_INPUTS.put("etProdBuy", "dialog_product_form.xml");
        MONEY_INPUTS.put("etProdSell", "dialog_product_form.xml");
    }

    /**
     * Kolom diskon sengaja TIDAK ada di daftar di atas.
     *
     * <p>Nilainya berarti dolar atau persen tergantung pemilih di sebelahnya,
     * jadi "$" yang menetap akan berbohong begitu pengguna memilih persen -
     * "$ 10" untuk potongan sepuluh persen. Tandanya dipasang saat berjalan
     * oleh {@code NativeCheckoutDialogFragment.applyDiscountAffix()}, dan
     * keberadaan metode itulah yang diperiksa di sini.
     */
    private static final String DISCOUNT_FIELD = "etDiscountValue";

    @Test
    public void everyMoneyInputShowsTheCurrencySymbol() throws IOException {
        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, String> entry : MONEY_INPUTS.entrySet()) {
            String fieldId = entry.getKey();
            String fileName = entry.getValue();
            String src = read(new File(resolve("src/main/res/layout"), fileName));

            int at = src.indexOf("android:id=\"@+id/" + fieldId + "\"");
            if (at < 0) {
                problems.add(fileName + ": kolom " + fieldId + " tidak ditemukan"
                        + " - kalau ia dipindah atau diganti nama, daftar di uji ini"
                        + " harus ikut, bukan dibiarkan diam-diam berhenti menjaga");
                continue;
            }

            if (!hasPrefixFromTextInputLayout(src, at) && !hasPrefixFromMoneyBox(src, at)) {
                problems.add(fileName + " / " + fieldId + ": tidak ada tanda \"$\""
                        + " yang terlihat - pakai app:prefixText pada TextInputLayout,"
                        + " atau bungkus dengan Widget.Valora.MoneyFieldBox");
            }
        }

        if (!problems.isEmpty()) {
            fail("Kolom isian uang tanpa tanda mata uang:\n  "
                    + String.join("\n  ", problems));
        }
    }

    @Test
    public void discountFieldSwitchesItsAffixWithTheMode() throws IOException {
        String dialog = read(new File(resolve("src/main/res/layout"),
                "dialog_native_checkout.xml"));
        int at = dialog.indexOf("android:id=\"@+id/" + DISCOUNT_FIELD + "\"");
        if (at >= 0 && hasPrefixFromTextInputLayout(dialog, at)) {
            fail(DISCOUNT_FIELD + ": tandanya dipaku di XML. Nilainya berarti dolar"
                    + " ATAU persen tergantung pemilih mode, jadi \"$\" yang menetap"
                    + " akan berbohong pada mode persen.");
        }

        String fragment = read(new File(resolve(
                "src/main/java/com/valdker/pos/ui/checkout"),
                "NativeCheckoutDialogFragment.java"));
        if (!fragment.contains("applyDiscountAffix")) {
            fail("NativeCheckoutDialogFragment tidak lagi memasang tanda diskon"
                    + " saat berjalan - kolom diskon jadi satu-satunya kolom uang"
                    + " tanpa keterangan mata uang sama sekali");
        }
        if (!fragment.contains("setSuffixText")) {
            fail("Mode persen tidak lagi menampilkan \"%\"");
        }
    }

    /**
     * Nilai yang dikirim ke server tetap desimal polos, apa pun yang diketik
     * atau DITEMPEL pengguna.
     *
     * <p>Menempel "$1,250.50" dari pesan WhatsApp ke kolom harga bukan hal
     * yang jarang, dan beberapa layar mengirim isi kolom langsung ke server.
     */
    @Test
    public void pastedCurrencyTextNeverReachesTheApi() {
        assertEquals("1250.50", Money.of("$1,250.50").toPlainString());
        assertEquals("1250.50", Money.of(" 1,250.5 ").toPlainString());
        assertEquals("1250.50", Money.of("USD 1250.50").toPlainString());
        assertEquals("0.00", Money.of("").toPlainString());
        assertEquals("0.00", Money.of("bukan angka").toPlainString());

        for (String raw : Arrays.asList("0", "1", "1.5", "10", "10.5",
                "1000", "1250.5", "1000000")) {
            String plain = Money.of(raw).toPlainString();
            if (plain.contains("$") || plain.contains(",")) {
                fail("nilai untuk API memuat simbol atau pemisah: " + plain);
            }
            if (!plain.matches("\\d+\\.\\d{2}")) {
                fail("nilai untuk API bukan desimal dua angka: " + plain);
            }
        }
    }

    /** TextInputLayout terdekat sebelum kolom memasang prefixText. */
    private boolean hasPrefixFromTextInputLayout(String src, int fieldAt) {
        int open = src.lastIndexOf(
                "<com.google.android.material.textfield.TextInputLayout", fieldAt);
        int close = src.lastIndexOf(
                "</com.google.android.material.textfield.TextInputLayout>", fieldAt);
        if (open < 0 || open < close) return false;
        String head = src.substring(open, src.indexOf('>', open));
        return head.contains("app:prefixText");
    }

    /** Kolom dibungkus kotak uang yang memuat TextView "$". */
    private boolean hasPrefixFromMoneyBox(String src, int fieldAt) {
        int open = src.lastIndexOf("Widget.Valora.MoneyFieldBox", fieldAt);
        if (open < 0) return false;
        int close = src.lastIndexOf("</LinearLayout>", fieldAt);
        if (close > open) return false;
        String box = src.substring(open, fieldAt);
        Matcher m = Pattern.compile("android:text=\"@string/money_prefix\"").matcher(box);
        return m.find();
    }

    private String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
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
