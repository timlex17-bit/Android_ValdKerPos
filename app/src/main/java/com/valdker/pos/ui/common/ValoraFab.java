package com.valdker.pos.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.valdker.pos.R;

/**
 * Tombol tambah mengambang yang menempatkan dirinya sendiri.
 *
 * <p>Sebelum ini tiap layar mengatur jarak FAB-nya sendiri dan hasilnya tidak
 * pernah sama. Ada tiga aturan yang hidup bersamaan:
 *
 * <ul>
 *   <li>Sebelas fragment memanggil {@code applyFabBottomInset(fab, 56)}, yang
 *       menambahkan 56dp DI ATAS bilah navigasi sistem. Angka itu dimaksudkan
 *       untuk menghindari bilah navigasi bawah aplikasi - padahal wadah
 *       fragment-nya memang sudah berhenti di atas bilah itu, jadi FAB-nya
 *       mengambang 56dp terlalu tinggi.</li>
 *   <li>Lima layar (BankAccountActivity, ProductsFragment, dan tiga layar
 *       bengkel) tidak memanggil apa pun, jadi FAB-nya duduk 16dp dari tepi
 *       jendela - yaitu DI BELAKANG bilah navigasi pada perangkat bergestur.</li>
 *   <li>HomeDashboardActivity punya penghitung inset sendiri lagi.</li>
 * </ul>
 *
 * <p>Sekarang aturannya satu dan tidak perlu diketahui layar mana pun: jarak
 * yang terlihat antara FAB dan penghalang terdekat di bawahnya selalu
 * {@code @dimen/fab_margin}. Penghalangnya bisa bilah navigasi sistem, bisa
 * bilah navigasi aplikasi, bisa tepi kartu - FAB menghitung sendiri seberapa
 * banyak inset sistem yang benar-benar menutupi dirinya lewat
 * {@link #overlapWithSystemBar(int, int)}, bukan menganggap setiap layar
 * digambar sampai tepi jendela.
 *
 * <p>Dipakai cukup dengan mengganti nama kelas di XML; tidak ada yang perlu
 * dipanggil dari Java. Tampilannya - warna merek, ikon tambah milik aplikasi,
 * ukuran - datang dari {@code @style/Widget.Valora.Fab}.
 */
public class ValoraFab extends FloatingActionButton {

    private int baseMarginPx;

    public ValoraFab(@NonNull Context context) {
        this(context, null);
    }

    public ValoraFab(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ValoraFab(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        baseMarginPx = getResources().getDimensionPixelSize(R.dimen.fab_margin);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        ViewCompat.setOnApplyWindowInsetsListener(this, (view, insets) -> {
            applyMargins(insets);
            return insets;
        });

        ViewCompat.requestApplyInsets(this);
    }

    private void applyMargins(@NonNull WindowInsetsCompat insets) {
        ViewGroup.LayoutParams params = getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;

        Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

        int bottom = baseMarginPx + overlapWithSystemBar(systemBars.bottom, Gravity.BOTTOM);
        int end = baseMarginPx + overlapWithSystemBar(systemBars.right, Gravity.END);

        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) params;
        if (lp.bottomMargin == bottom && lp.getMarginEnd() == end) return;

        lp.bottomMargin = bottom;
        lp.setMarginEnd(end);
        setLayoutParams(lp);
    }

    /**
     * Berapa banyak dari inset bilah sistem yang benar-benar menutupi FAB ini.
     *
     * <p>Kalau wadah FAB sudah berhenti di atas bilah sistem - misalnya layar
     * yang punya bilah navigasi aplikasi di bawahnya - maka inset itu sudah
     * ditangani orang lain dan menambahkannya lagi hanya akan mendorong FAB
     * naik tanpa alasan. Yang dihitung di sini adalah selisihnya saja.
     */
    private int overlapWithSystemBar(int inset, Gravity side) {
        if (inset <= 0) return 0;

        View parent = getParent() instanceof View ? (View) getParent() : null;
        if (parent == null) return inset;

        View root = getRootView();
        if (root == null) return inset;

        int[] location = new int[2];
        parent.getLocationInWindow(location);

        int freeSpaceAfterParent;
        if (side == Gravity.BOTTOM) {
            int parentBottom = location[1] + parent.getHeight();
            freeSpaceAfterParent = root.getHeight() - parentBottom;
        } else {
            int parentEnd = location[0] + parent.getWidth();
            freeSpaceAfterParent = root.getWidth() - parentEnd;
        }

        if (freeSpaceAfterParent <= 0) return inset;
        return Math.max(0, inset - freeSpaceAfterParent);
    }

    private enum Gravity {
        BOTTOM,
        END
    }
}
