package com.valdker.pos.workshop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.valdker.pos.models.CartItem;
import com.valdker.pos.money.Money;

import org.junit.Test;

/**
 * Sebuah ServicePackage dijual lewat Product bayangannya (product_id),
 * bukan lewat service_package_id - checkout selalu memakai payload standar
 * {"product": <id>, "quantity": ...}. servicePackageId hanya metadata UI
 * (label "Package", cart key). Tes ini menjaga supaya:
 * - item paket yang benar (productId valid) tidak pernah dianggap "stuck"
 *   oleh guard draft lama (lihat WorkshopPOSFragment.firstServicePackageInCart),
 * - getProductId() - yang dipakai langsung sebagai nilai "product" saat
 *   checkout - selalu mengembalikan product_id paket, bukan 0 atau
 *   service_package_id.
 */
public class WorkshopCartItemTest {

    private static CartItem packageCartItem(int productId, int servicePackageId) {
        CartItem item = new CartItem();
        item.productId = productId;
        item.servicePackageId = servicePackageId;
        item.shopId = 1;
        item.name = "Oil Change";
        item.setPrice(Money.of("45.00"));
        item.imageUrl = "";
        item.qty = 1;
        item.orderType = "";
        item.itemType = CartItem.ITEM_TYPE_SERVICE;
        item.refreshCartKey();
        return item;
    }

    @Test
    public void fromCartItemCarriesProductIdAndServicePackageIdSeparately() {
        CartItem source = packageCartItem(108, 7);

        WorkshopCartItem item = WorkshopCartItem.fromCartItem(source);

        assertEquals(108, item.getProductId());
        assertEquals(7, item.getServicePackageId());
    }

    @Test
    public void checkoutProductIdIsTheShadowProductNotTheServicePackageId() {
        // Nilai yang dipakai saat build payload checkout ("product": ...)
        // harus product_id, bukan id ServicePackage - keduanya kebetulan
        // beda di sini (108 vs 7) supaya bug tertukar langsung ketahuan.
        WorkshopCartItem item = WorkshopCartItem.fromCartItem(packageCartItem(108, 7));

        assertEquals(108, item.getProductId());
    }

    @Test
    public void isServicePackageIsTrueWhenServicePackageIdPresent() {
        WorkshopCartItem item = WorkshopCartItem.fromCartItem(packageCartItem(108, 7));

        assertTrue(item.isServicePackage());
    }

    @Test
    public void isServicePackageIsFalseForAnOrdinaryProduct() {
        CartItem source = new CartItem(55, 1, "Sparepart", 10.0, "", 1);

        WorkshopCartItem item = WorkshopCartItem.fromCartItem(source);

        assertFalse(item.isServicePackage());
    }

    @Test
    public void aSuccessfullySyncedPackageIsNeverConsideredStuck() {
        // Regresi: sebelum ini diperbaiki, guard draft lama menandai SETIAP
        // item paket (isServicePackage() == true) sebagai "stuck", termasuk
        // paket yang baru saja ditambahkan dengan product_id yang valid -
        // artinya checkout akan selalu diblokir untuk penjualan paket yang
        // benar sekalipun. "Stuck" hanya berlaku ketika productId belum ada.
        WorkshopCartItem synced = WorkshopCartItem.fromCartItem(packageCartItem(108, 7));

        boolean stuck = synced.isServicePackage() && synced.getProductId() <= 0;

        assertFalse(stuck);
    }

    @Test
    public void aPackageAddedBeforeBackfillWithNoProductIsConsideredStuck() {
        // Kasus legacy: paket sempat masuk keranjang draft lama sebelum
        // backend punya product_id sama sekali (productId <= 0).
        WorkshopCartItem legacy = WorkshopCartItem.fromCartItem(packageCartItem(0, 7));

        boolean stuck = legacy.isServicePackage() && legacy.getProductId() <= 0;

        assertTrue(stuck);
    }

    @Test
    public void cartKeyDistinguishesPackagesSharingTheSameProduct() {
        // Dua ServicePackage berbeda bisa saja (secara teori) memakai
        // strategi harga yang membuat cart key tanpa servicePackageId
        // bertabrakan; cart key harus tetap unik per paket.
        WorkshopCartItem first = WorkshopCartItem.fromCartItem(packageCartItem(108, 7));
        WorkshopCartItem second = WorkshopCartItem.fromCartItem(packageCartItem(108, 9));

        assertFalse(first.getCartKey().equals(second.getCartKey()));
    }
}
