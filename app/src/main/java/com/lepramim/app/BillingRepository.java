package com.lepramim.app;

import android.app.Activity;
import android.content.Context;

final class BillingRepository {
    static final String PLAN_MONTHLY = BillingManager.PLAN_MONTHLY;
    static final String PLAN_ANNUAL = BillingManager.PLAN_ANNUAL;

    private final BillingManager billingManager;

    BillingRepository(Context context, BillingManager.Listener listener) {
        billingManager = new BillingManager(context, listener);
    }

    void start() {
        billingManager.startConnection();
    }

    void buy(Activity activity, String plan) {
        billingManager.launchSubscriptionPurchase(activity, plan);
    }

    void restore() {
        billingManager.restorePurchases();
    }

    void refresh() {
        billingManager.refreshPurchases();
    }

    void release() {
        billingManager.release();
    }
}
