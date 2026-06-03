package com.valdker.pos.models;

import org.json.JSONObject;

public class ProductUnit {

    public int id;
    public int product;
    public String unit_name = "";
    public double conversion_qty = 1.0;
    public String barcode = "";
    public boolean is_base_unit;
    public boolean is_default_purchase_unit;
    public boolean is_default_sale_unit;
    private int unitId;
    private boolean productUnitIdTrusted;

    public ProductUnit() {
    }

    public static ProductUnit fromJson(JSONObject obj) {
        ProductUnit unit = new ProductUnit();
        if (obj == null) return unit;

        int explicitProductUnitId = obj.optInt("product_unit", obj.optInt("product_unit_id", 0));
        int rawId = obj.optInt("id");
        unit.id = explicitProductUnitId > 0 ? explicitProductUnitId : rawId;
        unit.product = obj.optInt("product", obj.optInt("product_id", 0));
        unit.unitId = obj.optInt("unit", obj.optInt("unit_id", obj.optInt("base_unit", 0)));
        unit.unit_name = obj.optString("unit_name", obj.optString("name", ""));
        unit.conversion_qty = obj.optDouble("conversion_qty", obj.optDouble("conversion_factor", 1.0));
        unit.barcode = obj.optString("barcode", "");
        unit.is_base_unit = obj.optBoolean("is_base_unit", obj.optBoolean("is_base", false));
        unit.is_default_purchase_unit = obj.optBoolean("is_default_purchase_unit", false);
        unit.is_default_sale_unit = obj.optBoolean("is_default_sale_unit", false);
        unit.productUnitIdTrusted = explicitProductUnitId > 0
                || (rawId > 0 && (obj.has("product") || obj.has("product_id")));

        return unit;
    }

    public int getId() {
        return id;
    }

    public int getProduct() {
        return product;
    }

    public int getUnitId() {
        return unitId;
    }

    public String getUnitName() {
        return unit_name == null ? "" : unit_name;
    }

    public double getConversionQty() {
        return conversion_qty <= 0 ? 1.0 : conversion_qty;
    }

    public String getBarcode() {
        return barcode == null ? "" : barcode;
    }

    public boolean isBaseUnit() {
        return is_base_unit;
    }

    public boolean isDefaultPurchaseUnit() {
        return is_default_purchase_unit;
    }

    public boolean isDefaultSaleUnit() {
        return is_default_sale_unit;
    }

    public boolean hasValidProductUnitIdForProduct(int productId) {
        return productUnitIdTrusted
                && id > 0
                && (product <= 0 || productId <= 0 || product == productId);
    }

    public String getDisplayName() {
        String name = getUnitName().trim();
        if (name.isEmpty()) {
            name = "Unit #" + id;
        }

        if (is_base_unit) return name + " (Base)";
        return name;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
