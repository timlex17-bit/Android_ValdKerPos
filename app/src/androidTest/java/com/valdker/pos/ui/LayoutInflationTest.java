package com.valdker.pos.ui;

import static org.junit.Assert.assertNotNull;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.valdker.pos.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Menjaga agar tata letak yang dirombak saat audit UI benar-benar bisa
 * di-inflate.
 *
 * <p>Kesalahan yang paling mudah lolos saat menyusun ulang layout bukan
 * kesalahan kompilasi: gaya yang mewarisi induk yang salah, atribut yang hanya
 * berlaku pada induk tertentu, atau &lt;include&gt; yang menunjuk berkas yang
 * memerlukan tema tertentu, semuanya lolos aapt dan baru meledak sebagai
 * InflateException di tangan pengguna. Beberapa layar di daftar ini hanya bisa
 * dibuka dengan jenis usaha atau peran tertentu, jadi menjalankan aplikasi satu
 * kali tidak pernah menyentuh semuanya.
 */
@RunWith(AndroidJUnit4.class)
public class LayoutInflationTest {

    private static final int[] LAYOUTS = {
            // Kerangka bersama
            R.layout.view_topbar,
            R.layout.view_offline_stat,
            R.layout.view_draft_chip,
            R.layout.view_pos_draft_row,
            R.layout.view_pos_category_bar,

            // POS kasir
            R.layout.view_pos_topbar,
            R.layout.view_pos_controls,
            R.layout.view_pos_list,
            R.layout.view_pos_checkout,
            R.layout.fragment_retail_pos,
            R.layout.fragment_workshop_pos,
            R.layout.activity_main,

            // Keranjang kasir. Pada tablet, R.layout.fragment_cart menunjuk
            // varian layout-sw600dp yang TIDAK punya viewDim maupun
            // btnCloseCart - jalur null-safe di CartFragment hanya benar
            // selama varian itu benar-benar bisa di-inflate.
            R.layout.fragment_cart,
            R.layout.item_cart,
            R.layout.item_category_chip,
            R.layout.item_product_grid,
            R.layout.item_product_list,
            R.layout.item_product_manage,

            // Layar ber-FAB. ValoraFab memasang pendengar inset saat
            // menempel ke jendela, jadi kegagalan inflate-nya hanya terlihat
            // di perangkat sungguhan.
            R.layout.fragment_customers,
            R.layout.fragment_suppliers,
            R.layout.fragment_units,
            R.layout.fragment_expenses,
            R.layout.fragment_purchases,
            R.layout.fragment_warehouses,
            R.layout.fragment_warehouse_stocks,
            R.layout.fragment_stock_transfers,
            R.layout.fragment_stock_adjustments,
            R.layout.fragment_manage_categories,
            R.layout.fragment_products_manage,
            R.layout.fragment_product_returns,
            R.layout.fragment_inventory_count_list,
            R.layout.fragment_products,

            // Pesanan Offline
            R.layout.activity_pending_orders,
            R.layout.item_pending_order,

            // Owner Chat
            R.layout.activity_owner_chat,
            R.layout.item_owner_chat_bot,
            R.layout.item_owner_chat_user,
            R.layout.item_owner_chat_typing,
            R.layout.item_owner_chat_link,

            // Papan Dapur
            R.layout.activity_kitchen_display,
            R.layout.view_kitchen_stat,
            R.layout.item_kitchen_order,
            R.layout.item_kitchen_item,

            // Layar daftar & detail
            R.layout.activity_workshop_module_list,
            R.layout.activity_printer_settings,
            R.layout.activity_product_return_detail,
            R.layout.activity_stock_adjustment_detail,
            R.layout.activity_stock_movement_detail,
            R.layout.activity_inventory_count_detail,

            // Masuk & onboarding (strip bilah status)
            R.layout.activity_login,
            R.layout.activity_splash_screen_1,
            R.layout.activity_splash_screen_2,
            R.layout.activity_splash_screen_3,
    };

    /**
     * Setiap dialog formulir, karena radius 3dp datang dari gaya bersama dan
     * gaya tombol dialog punya syarat pewarisan yang mudah dilanggar.
     */
    private static final int[] DIALOGS = {
            R.layout.dialog_bank_account,
            // Popup pembayaran: TextInputLayout dan kartu bagian di dalamnya
            // butuh tema Material, dan itu baru terbukti saat di-inflate.
            R.layout.dialog_native_checkout,
            R.layout.item_split_payment,
            R.layout.dialog_category_form,
            R.layout.dialog_close_shift,
            R.layout.dialog_customer_form,
            R.layout.dialog_expense_form,
            R.layout.dialog_inventory_count,
            R.layout.dialog_inventory_count_form,
            R.layout.dialog_mechanic_form,
            R.layout.dialog_product_form,
            R.layout.dialog_product_return_add,
            R.layout.dialog_purchase_add,
            R.layout.dialog_service_package_form,
            R.layout.dialog_shift_open,
            R.layout.dialog_stock_adjustment,
            R.layout.dialog_stock_transfer_form,
            R.layout.dialog_supplier_form,
            R.layout.dialog_unit_form,
            R.layout.dialog_vehicle_input,
            R.layout.dialog_warehouse_form,
            R.layout.dialog_warehouse_stock_form,
            R.layout.popup_user_menu,
            R.layout.view_app_popup_toast,
    };

    @Test
    public void screenLayoutsInflate() {
        inflateAll(LAYOUTS);
    }

    @Test
    public void dialogLayoutsInflate() {
        inflateAll(DIALOGS);
    }

    /**
     * Inflate dijalankan di dalam Activity sungguhan agar memakai jalur
     * inflater yang sama dengan aplikasi - lihat TestHostActivity.
     */
    private void inflateAll(int[] layouts) {
        try (ActivityScenario<TestHostActivity> scenario =
                     ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                LayoutInflater inflater = activity.getLayoutInflater();
                FrameLayout parent = activity.container();

                for (int layout : layouts) {
                    String name = activity.getResources().getResourceEntryName(layout);
                    try {
                        View view = inflater.inflate(layout, parent, false);
                        assertNotNull(name, view);
                    } catch (RuntimeException e) {
                        throw new AssertionError("Gagal inflate " + name + ": " + e.getMessage(), e);
                    }
                }
            });
        }
    }
}
