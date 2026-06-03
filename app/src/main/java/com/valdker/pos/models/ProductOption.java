package com.valdker.pos.models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ProductOption {

    private int id;
    private String name;
    private String code;
    private String sku;
    private String itemType;
    private final List<ProductUnit> productUnits = new ArrayList<>();

    public static ProductOption fromJson(JSONObject obj) {
        ProductOption product = new ProductOption();

        if (obj == null) return product;

        product.id = obj.optInt("id");
        product.name = obj.optString("name", "");
        product.code = obj.optString("code", "");
        product.sku = obj.optString("sku", "");
        product.itemType = obj.optString("item_type", "");
        JSONArray units = obj.optJSONArray("product_units");
        if (units == null) {
            units = obj.optJSONArray("units");
        }
        product.setProductUnits(parseProductUnits(units));

        return product;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getSku() {
        return sku;
    }

    public String getItemType() {
        return itemType == null ? "" : itemType;
    }

    public List<ProductUnit> getProductUnits() {
        return productUnits;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public void setCode(String code) {
        this.code = code == null ? "" : code;
    }

    public void setSku(String sku) {
        this.sku = sku == null ? "" : sku;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType == null ? "" : itemType;
    }

    public void setProductUnits(List<ProductUnit> units) {
        productUnits.clear();
        if (units != null) {
            productUnits.addAll(units);
        }
    }

    public String getDisplayName() {
        String display = name == null || name.trim().isEmpty() ? "Product #" + id : name.trim();

        if (sku != null && !sku.trim().isEmpty()) {
            display += " - SKU: " + sku.trim();
        }

        if (itemType != null && !itemType.trim().isEmpty()) {
            display += " (" + itemType.trim() + ")";
        }

        return display;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    private static List<ProductUnit> parseProductUnits(JSONArray arr) {
        List<ProductUnit> units = new ArrayList<>();
        if (arr == null) return units;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject unitObj = arr.optJSONObject(i);
            if (unitObj != null) {
                units.add(ProductUnit.fromJson(unitObj));
            }
        }

        return units;
    }
}
