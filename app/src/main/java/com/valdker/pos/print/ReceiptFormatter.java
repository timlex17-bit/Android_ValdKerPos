package com.valdker.pos.print;

public class ReceiptFormatter {

    public static String build(OrderData o) {
        StringBuilder sb = new StringBuilder();

        sb.append("[C]<b>").append(safe(o.shopName)).append("</b>\n");
        if (!safe(o.shopAddress).isEmpty()) sb.append("[C]").append(safe(o.shopAddress)).append("\n");
        if (!safe(o.shopPhone).isEmpty()) sb.append("[C]").append(safe(o.shopPhone)).append("\n");

        sb.append("[C]--------------------------------\n");
        sb.append("[L]INV: ").append(safe(o.invoice)).append("\n");
        sb.append("[L]Cashier: ").append(safe(o.cashier)).append("\n");
        if (!safe(o.customer).isEmpty()) {
            sb.append("[L]Customer: ").append(safe(o.customer)).append("\n");
        }
        sb.append("[L]Date: ").append(safe(o.date)).append("\n");
        sb.append("[C]--------------------------------\n");

        for (OrderItemData it : o.items) {
            if (it == null) continue;
            sb.append("[L]<b>").append(safe(it.name)).append("</b>")
                    .append("[R]<b>$").append(fmt(it.lineTotal)).append("</b>\n");

            String label = typeLabel(it.orderType);
            if (!label.isEmpty()) {
                sb.append("[L](").append(typeIcon(it.orderType)).append(" ").append(label).append(")\n");
            }

            sb.append("[L]").append(Math.max(0, it.qty)).append(" x $").append(fmt(it.unitPrice)).append("\n");
        }

        sb.append("[C]--------------------------------\n");
        sb.append("[L]Subtotal[R]$").append(fmt(o.subtotal)).append("\n");
        sb.append("[L]Discount[R]$").append(fmt(o.discount)).append("\n");
        sb.append("[L]Tax[R]$").append(fmt(o.tax)).append("\n");
        sb.append("[L]<b>Total</b>[R]<b>$").append(fmt(o.total)).append("</b>\n");
        if (!safe(o.paymentMethod).isEmpty()) {
            sb.append("[L]Payment[R]").append(safe(o.paymentMethod)).append("\n");
        }
        if (o.paidAmount > 0) {
            sb.append("[L]Paid[R]$").append(fmt(o.paidAmount)).append("\n");
            sb.append("[L]Change[R]$").append(fmt(o.changeAmount)).append("\n");
        }
        sb.append("[C]--------------------------------\n");
        sb.append("[C]Thank you!\n\n\n");

        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.2f", Math.max(0, v));
    }

    private static String typeLabel(String t) {
        if ("DINE_IN".equals(t)) return "DINE IN";
        if ("TAKE_OUT".equals(t)) return "TAKE OUT";
        if ("DELIVERY".equals(t)) return "DELIVERY";
        return "";
    }

    private static String typeIcon(String t) {
        if ("DINE_IN".equals(t)) return "**";
        if ("TAKE_OUT".equals(t)) return "*";
        if ("DELIVERY".equals(t)) return "^";
        return "-";
    }
}
