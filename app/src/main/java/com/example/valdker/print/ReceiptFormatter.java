package com.example.valdker.print;

public class ReceiptFormatter {

    public static String build(OrderData o) {
        StringBuilder sb = new StringBuilder();

        sb.append("[C]<b>").append(safe(o.shopName)).append("</b>\n");
        if (o.shopAddress != null && !o.shopAddress.isEmpty())
            sb.append("[C]").append(o.shopAddress).append("\n");
        if (o.shopPhone != null && !o.shopPhone.isEmpty())
            sb.append("[C]").append(o.shopPhone).append("\n");

        sb.append("[C]--------------------------------\n");

        sb.append("[L]INV: ").append(safe(o.invoice)).append("\n");
        sb.append("[L]Kasir: ").append(safe(o.cashier)).append("\n");
        sb.append("[L]Date: ").append(safe(o.date)).append("\n");

        sb.append("[C]--------------------------------\n");

        for (OrderItemData it : o.items) {
            // Row 1: name left, total right
            sb.append("[L]<b>").append(safe(it.name)).append("</b>")
                    .append("[R]<b>$").append(fmt(it.lineTotal)).append("</b>\n");

            // Row 2: order type badge
            sb.append("[L](").append(typeIcon(it.orderType)).append(" ").append(typeLabel(it.orderType)).append(")\n");

            // Row 3: qty x price
            sb.append("[L]").append(it.qty).append(" x $").append(fmt(it.unitPrice)).append("\n");
        }

        sb.append("[C]--------------------------------\n");
        sb.append("[L]Subtotal[R]$").append(fmt(o.subtotal)).append("\n");
        sb.append("[L]Discount[R]$").append(fmt(o.discount)).append("\n");
        sb.append("[L]Tax[R]$").append(fmt(o.tax)).append("\n");
        sb.append("[L]<b>Total</b>[R]<b>$").append(fmt(o.total)).append("</b>\n");
        sb.append("[C]--------------------------------\n");
        sb.append("[C]Thank you!\n\n\n");

        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private static String typeLabel(String t) {
        if ("DINE_IN".equals(t)) return "DINE IN";
        if ("TAKE_OUT".equals(t)) return "TAKE OUT";
        if ("DELIVERY".equals(t)) return "DELIVERY";
        return t == null ? "-" : t;
    }

    private static String typeIcon(String t) {
        if ("DINE_IN".equals(t)) return "■■";
        if ("TAKE_OUT".equals(t)) return "■";
        if ("DELIVERY".equals(t)) return "▲";
        return "•";
    }
}
