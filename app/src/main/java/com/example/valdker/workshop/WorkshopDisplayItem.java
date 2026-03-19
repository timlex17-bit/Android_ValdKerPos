package com.example.valdker.workshop;

public class WorkshopDisplayItem {

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_ITEM = 1;

    private int viewType;
    private String headerTitle;
    private WorkshopCartItem cartItem;

    private WorkshopDisplayItem(int viewType, String headerTitle, WorkshopCartItem cartItem) {
        this.viewType = viewType;
        this.headerTitle = headerTitle;
        this.cartItem = cartItem;
    }

    public static WorkshopDisplayItem createHeader(String title) {
        return new WorkshopDisplayItem(VIEW_TYPE_HEADER, title, null);
    }

    public static WorkshopDisplayItem createItem(WorkshopCartItem item) {
        return new WorkshopDisplayItem(VIEW_TYPE_ITEM, null, item);
    }

    public int getViewType() {
        return viewType;
    }

    public String getHeaderTitle() {
        return headerTitle;
    }

    public WorkshopCartItem getCartItem() {
        return cartItem;
    }
}