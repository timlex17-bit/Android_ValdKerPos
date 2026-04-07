package com.valdker.pos.models;

import org.json.JSONArray;
import org.json.JSONObject;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class ProductReturn implements Parcelable {
    public int id;
    public Integer order;              // order id
    public String invoiceNumber = "";  // ✅ NEW
    public CustomerLite customer;      // nested
    public String note;
    public String returnedAt;          // ISO Z
    public UserLite returnedBy;        // nested
    public List<ProductReturnItem> items = new ArrayList<>();

    // ✅ For Parcelable safe transport (no need other models Parcelable)
    private String customerJson = "";
    private String returnedByJson = "";
    private String itemsJson = "[]";

    public ProductReturn() {}

    public static ProductReturn fromJson(JSONObject o) {
        ProductReturn pr = new ProductReturn();

        pr.id = o.optInt("id");
        pr.order = o.has("order") && !o.isNull("order") ? o.optInt("order") : null;

        // ✅ NEW: invoice_number from API (flat field)
        pr.invoiceNumber = o.optString("invoice_number", "");

        // ✅ customer is OBJECT {id, name}
        JSONObject c = o.optJSONObject("customer");
        if (c != null) {
            pr.customer = CustomerLite.fromJson(c);
            pr.customerJson = c.toString();
        } else {
            pr.customerJson = "";
        }

        pr.note = o.optString("note", "");
        pr.returnedAt = o.optString("returned_at", "");

        // ✅ returned_by is OBJECT {id, username, display_name}
        JSONObject u = o.optJSONObject("returned_by");
        if (u != null) {
            pr.returnedBy = UserLite.fromJson(u);
            pr.returnedByJson = u.toString();
        } else {
            pr.returnedByJson = "";
        }

        // ✅ items is ARRAY
        JSONArray arr = o.optJSONArray("items");
        pr.items = new ArrayList<>();
        if (arr != null) {
            pr.itemsJson = arr.toString();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.optJSONObject(i);
                if (it != null) pr.items.add(ProductReturnItem.fromJson(it));
            }
        } else {
            pr.itemsJson = "[]";
        }

        return pr;
    }

    public int itemsCount() {
        return items == null ? 0 : items.size();
    }

    public double totalQty() {
        double sum = 0;
        if (items != null) for (ProductReturnItem it : items) sum += it.quantity;
        return sum;
    }

    public double totalAmount() {
        double sum = 0;
        if (items != null) for (ProductReturnItem it : items) sum += it.lineTotal();
        return sum;
    }

    // =========================
    // Parcelable Implementation
    // =========================

    protected ProductReturn(Parcel in) {
        id = in.readInt();
        order = (Integer) in.readValue(Integer.class.getClassLoader());
        invoiceNumber = in.readString();
        note = in.readString();
        returnedAt = in.readString();

        customerJson = in.readString();
        returnedByJson = in.readString();
        itemsJson = in.readString();

        // Rebuild nested objects from stored json (safe)
        try {
            if (customerJson != null && !customerJson.trim().isEmpty()) {
                customer = CustomerLite.fromJson(new JSONObject(customerJson));
            }
        } catch (Exception ignored) {}

        try {
            if (returnedByJson != null && !returnedByJson.trim().isEmpty()) {
                returnedBy = UserLite.fromJson(new JSONObject(returnedByJson));
            }
        } catch (Exception ignored) {}

        items = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(itemsJson != null ? itemsJson : "[]");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.optJSONObject(i);
                if (it != null) items.add(ProductReturnItem.fromJson(it));
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeValue(order);
        dest.writeString(invoiceNumber);
        dest.writeString(note);
        dest.writeString(returnedAt);

        // store json versions so no need other models implement Parcelable
        dest.writeString(customerJson != null ? customerJson : "");
        dest.writeString(returnedByJson != null ? returnedByJson : "");
        dest.writeString(itemsJson != null ? itemsJson : "[]");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ProductReturn> CREATOR = new Creator<ProductReturn>() {
        @Override
        public ProductReturn createFromParcel(Parcel in) {
            return new ProductReturn(in);
        }

        @Override
        public ProductReturn[] newArray(int size) {
            return new ProductReturn[size];
        }
    };
}