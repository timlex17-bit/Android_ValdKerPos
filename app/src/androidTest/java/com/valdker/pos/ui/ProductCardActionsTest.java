package com.valdker.pos.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.text.Layout;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.button.MaterialButton;
import com.valdker.pos.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Menjaga sepasang tombol Edit/Hapus pada kartu produk tetap kembar.
 *
 * <p>Kartu produk tampil dalam grid, jadi lebar tombolnya bukan angka yang
 * ditulis di layout melainkan hasil bagi lebar kolom. Di ponsel 360dp satu
 * tombol hanya kebagian sekitar 57dp, dan pada lebar itu MaterialButton
 * melipat labelnya: "Delete" menjadi "Del" di atas dan "ete" di bawah. Tombol
 * yang terlipat lebih tinggi daripada tetangganya, jadi pasangan itu berhenti
 * sejajar - terukur di ponsel pemilik sebagai Edit 57x40dp dan Delete
 * 57x45dp.
 *
 * <p>Tiga hal yang diperiksa di sini, karena ketiganya bisa rusak sendiri-
 * sendiri: labelnya satu baris, labelnya tidak dipotong elipsis, dan kedua
 * tombol punya lebar serta tinggi yang sama persis.
 *
 * <p>Diuji pada lebar kartu tersempit yang mungkin (grid dua kolom di layar
 * 320dp) sampai lebar tablet, dan pada skala huruf sampai 1,5x - setelan
 * "Largest" yang lazim dipilih pemilik toko. Ukuran huruf boleh menyusut
 * (gaya Compact memakai autosize); yang tidak boleh adalah melipat atau
 * terpotong.
 */
@RunWith(AndroidJUnit4.class)
public class ProductCardActionsTest {

    /**
     * Pasangan (lebar kartu, skala huruf) yang benar-benar bisa terjadi.
     *
     * <p>Bukan perkalian semua lawan semua, karena kedua angka itu tidak
     * saling bebas: jumlah kolom grid ditentukan
     * ProductsManageFragment.getResponsiveProductSpan(), yang tidak pernah
     * membuat kolom lebih sempit daripada @dimen/product_card_min_width dan
     * mengurangi satu kolom begitu skala huruf mencapai 1,3x. Jadi kartu
     * selebar 150dp hanya mungkin pada huruf bawaan; pada huruf "Large" dan
     * "Largest" ponsel selalu turun ke satu kolom, dan kartunya jadi selebar
     * layar.
     *
     * <p>Menguji kombinasi yang tidak mungkin terjadi hanya akan memaksa
     * desain melayani keadaan yang tidak pernah ada.
     */
    private static final float[][] CASES = {
            // {lebar kartu dp, skala huruf}
            {150, 1.0f},   // ponsel 360dp, dua kolom
            {160, 1.0f},   // ponsel 411dp, dua kolom
            {150, 1.15f},  // ponsel 360dp, huruf sedikit dibesarkan
            {200, 1.0f},   // kolom tablet
            {240, 1.0f},   // kolom tablet lebar
            {280, 1.3f},   // ponsel "Large": satu kolom
            {280, 1.5f},   // ponsel "Largest": satu kolom
            {320, 1.5f},   // tablet "Largest": kolom berkurang satu
    };

    @Test
    public void editAndDeleteStayTwins() {
        List<String> problems = new ArrayList<>();

        try (ActivityScenario<TestHostActivity> scenario =
                     ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                for (float[] testCase : CASES) {
                    int widthDp = (int) testCase[0];
                    float scale = testCase[1];

                    Configuration config =
                            new Configuration(activity.getResources().getConfiguration());
                    config.fontScale = scale;
                    Context scaled = activity.createConfigurationContext(config);
                    scaled.setTheme(R.style.Theme_ValdKer);
                    LayoutInflater inflater =
                            activity.getLayoutInflater().cloneInContext(scaled);

                    {
                        int widthPx = (int) TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP, widthDp,
                                scaled.getResources().getDisplayMetrics());

                        View card = inflater.inflate(
                                R.layout.item_product_manage, activity.container(), false);
                        card.measure(
                                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                        card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());

                        MaterialButton edit = card.findViewById(R.id.btnEdit);
                        MaterialButton delete = card.findViewById(R.id.btnDelete);
                        String where = widthDp + "dp @ font " + scale + ": ";

                        check(problems, where, "Edit", edit);
                        check(problems, where, "Delete", delete);

                        if (edit.getHeight() != delete.getHeight()) {
                            problems.add(where + "tinggi berbeda - Edit " + edit.getHeight()
                                    + "px, Delete " + delete.getHeight() + "px");
                        }
                        if (edit.getWidth() != delete.getWidth()) {
                            problems.add(where + "lebar berbeda - Edit " + edit.getWidth()
                                    + "px, Delete " + delete.getWidth() + "px");
                        }
                    }
                }
            });
        }

        if (!problems.isEmpty()) {
            throw new AssertionError("Tombol kartu produk tidak kembar:\n  "
                    + String.join("\n  ", problems));
        }
    }

    private void check(List<String> problems, String where, String name, MaterialButton button) {
        if (button == null) {
            problems.add(where + name + " tidak ditemukan");
            return;
        }
        Layout layout = button.getLayout();
        if (layout == null) {
            problems.add(where + name + " tidak sempat diukur");
            return;
        }
        if (layout.getLineCount() != 1) {
            problems.add(where + name + " terlipat " + layout.getLineCount() + " baris"
                    + " (\"" + button.getText() + "\")");
        }
        if (layout.getEllipsisCount(0) > 0) {
            // Angka-angka ini yang menunjukkan penyebabnya saat uji gagal:
            // apakah kotaknya yang kurang lebar, atau hurufnya yang sudah
            // mentok di batas bawah autosize dan tetap tidak muat.
            float density = button.getResources().getDisplayMetrics().density;
            int inner = button.getWidth() - button.getPaddingLeft() - button.getPaddingRight();
            problems.add(where + name + " terpotong elipsis (\"" + button.getText() + "\")"
                    + " [lebar dalam " + Math.round(inner / density) + "dp, huruf "
                    + Math.round(button.getTextSize() / density) + "dp, butuh "
                    + Math.round(layout.getPaint().measureText(button.getText().toString()) / density) + "dp]");
        }
    }
}
