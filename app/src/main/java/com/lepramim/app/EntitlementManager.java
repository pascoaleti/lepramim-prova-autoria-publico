package com.lepramim.app;

import android.content.Context;

final class EntitlementManager {
    private final Context appContext;

    EntitlementManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    boolean hasPlus() {
        return EntitlementStore.hasUnlimitedAccess(appContext);
    }

    boolean hasPaidSubscription() {
        return EntitlementStore.isSubscriptionActive(appContext);
    }

    boolean tryConsumeScreenRead() {
        return EntitlementStore.tryConsumeScreenRead(appContext);
    }

    boolean tryConsumeImageRead() {
        return EntitlementStore.tryConsumeImageRead(appContext);
    }

    String planLabel() {
        return EntitlementStore.getPlanLabel(appContext);
    }

    String screenLimitMessage() {
        return EntitlementStore.getScreenLimitReachedMessage(appContext);
    }

    String imageLimitMessage() {
        return EntitlementStore.getImageLimitReachedMessage(appContext);
    }

    String imageRemainingMessage() {
        return EntitlementStore.getImageRemainingMessage(appContext);
    }
}
