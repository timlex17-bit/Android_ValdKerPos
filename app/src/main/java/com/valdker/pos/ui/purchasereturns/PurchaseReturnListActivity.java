package com.valdker.pos.ui.purchasereturns;

import androidx.annotation.NonNull;

import com.valdker.pos.ModuleRegistry;
import com.valdker.pos.ui.workshop.WorkshopSimpleListActivity;

public class PurchaseReturnListActivity extends WorkshopSimpleListActivity {
    @NonNull
    @Override
    protected String moduleKey() {
        return ModuleRegistry.PURCHASE_RETURNS;
    }

    @NonNull
    @Override
    protected String endpoint() {
        return "api/purchase-returns/";
    }

    @NonNull
    @Override
    protected String screenTitle() {
        return "Purchase Returns";
    }

    @NonNull
    @Override
    protected String emptyMessage() {
        return "No purchase returns found";
    }
}
