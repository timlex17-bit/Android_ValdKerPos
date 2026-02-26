package com.example.valdker.models;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Order {
    private final int id;

    @Nullable
    private final Integer customerId;

    private final String createdAtIso;      // "2026-02-13T16:31:06.847166Z"
    private final String paymentMethod;

    private final double subtotal;
    private final double discount;
    private final double tax;
    private final double total;

    private final String notes;
    private final boolean isPaid;

    private final List<OrderItem> items;

    public Order(
            int id,
            @Nullable Integer customerId,
            String invoiceNumber,
            String createdAtIso,
            String paymentMethod,
            double subtotal,
            double discount,
            double tax,
            double total,
            String notes,
            boolean isPaid,
            List<OrderItem> items
    ) {
        this.id = id;
        this.customerId = customerId;
        this.invoiceNumber = invoiceNumber != null ? invoiceNumber : "";
        this.createdAtIso = createdAtIso != null ? createdAtIso : "";
        this.paymentMethod = paymentMethod != null ? paymentMethod : "";
        this.subtotal = subtotal;
        this.discount = discount;
        this.tax = tax;
        this.total = total;
        this.notes = notes != null ? notes : "";
        this.isPaid = isPaid;
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    @Nullable
    public Integer getCustomerId() {
        return customerId;
    }

    public String getCreatedAtIso() {
        return createdAtIso;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public double getSubtotal() {
        return subtotal;
    }

    public double getDiscount() {
        return discount;
    }

    public double getTax() {
        return tax;
    }

    public double getTotal() {
        return total;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isPaid() {
        return isPaid;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public int getItemsCount() {
        return items != null ? items.size() : 0;
    }

    private final String invoiceNumber;

    public String getInvoiceNumber() {
        return invoiceNumber;
    }
}
