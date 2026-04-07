package com.valdker.pos.print;

import java.util.ArrayList;
import java.util.List;

public class OrderData {
    public String shopName;
    public String shopAddress;
    public String shopPhone;

    public String invoice;
    public String cashier;
    public String date;

    public double subtotal;
    public double discount;
    public double tax;
    public double total;

    public List<OrderItemData> items = new ArrayList<>();
}
