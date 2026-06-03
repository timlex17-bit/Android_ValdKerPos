package com.valdker.pos.models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ProductScanResponse {

    public int product_id;
    public String product_name = "";
    public String product_sku = "";
    public String product_code = "";
    public String product_barcode = "";
    public String product_item_type = "";

    public ProductUnit matched_unit;
    public List<ProductUnit> product_units = new ArrayList<>();
    public double conversion_qty = 1.0;
    public String barcode_type = "";

    public static ProductScanResponse fromJson(JSONObject obj) {
        ProductScanResponse response = new ProductScanResponse();
        if (obj == null) return response;

        JSONObject productObj = obj.optJSONObject("product");
        if (productObj != null) {
            response.product_id = productObj.optInt("id");
            response.product_name = productObj.optString("name", "");
            response.product_sku = productObj.optString("sku", "");
            response.product_code = productObj.optString("code", "");
            response.product_barcode = productObj.optString("barcode", "");
            response.product_item_type = productObj.optString("item_type", "");
            if (response.product_barcode.trim().isEmpty()) {
                response.product_barcode = productObj.optString("code", "");
            }
            response.product_units.addAll(parseProductUnits(productObj.optJSONArray("product_units")));
        } else {
            response.product_id = obj.optInt("product_id", obj.optInt("id"));
            response.product_name = obj.optString("product_name", obj.optString("name", ""));
            response.product_sku = obj.optString("product_sku", obj.optString("sku", ""));
            response.product_code = obj.optString("product_code", obj.optString("code", ""));
            response.product_barcode = obj.optString("product_barcode", obj.optString("barcode", ""));
            response.product_item_type = obj.optString("product_item_type", obj.optString("item_type", ""));
        }

        JSONObject matchedUnitObj = obj.optJSONObject("matched_unit");
        if (matchedUnitObj != null) {
            response.matched_unit = ProductUnit.fromJson(matchedUnitObj);
        }

        JSONArray unitsArr = obj.optJSONArray("product_units");
        if (unitsArr != null) {
            response.product_units.clear();
            response.product_units.addAll(parseProductUnits(unitsArr));
        }

        response.conversion_qty = obj.optDouble(
                "conversion_qty",
                response.matched_unit != null ? response.matched_unit.getConversionQty() : 1.0
        );
        response.barcode_type = obj.optString("barcode_type", "");

        return response;
    }

    public ProductOption toProductOption() {
        ProductOption product = new ProductOption();
        product.setId(product_id);
        product.setName(product_name);
        product.setSku(product_sku);
        product.setCode(product_code);
        product.setItemType(product_item_type);
        product.setProductUnits(product_units);
        return product;
    }

    public int getProductId() {
        return product_id;
    }

    public String getProductName() {
        return product_name == null ? "" : product_name;
    }

    public String getProductSku() {
        return product_sku == null ? "" : product_sku;
    }

    public String getProductCode() {
        return product_code == null ? "" : product_code;
    }

    public String getProductBarcode() {
        return product_barcode == null ? "" : product_barcode;
    }

    public String getProductItemType() {
        return product_item_type == null ? "" : product_item_type;
    }

    public ProductUnit getMatchedUnit() {
        return matched_unit;
    }

    public List<ProductUnit> getProductUnits() {
        return product_units;
    }

    public double getConversionQty() {
        return conversion_qty <= 0 ? 1.0 : conversion_qty;
    }

    public String getBarcodeType() {
        return barcode_type == null ? "" : barcode_type;
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
