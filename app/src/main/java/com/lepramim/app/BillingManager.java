package com.lepramim.app;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class BillingManager implements PurchasesUpdatedListener {
    static final String PLAN_MONTHLY = "monthly";
    static final String PLAN_ANNUAL = "annual";
    static final String MONTHLY_PRODUCT_ID = "lepramim_plus_monthly";
    static final String ANNUAL_PRODUCT_ID = "lepramim_plus_annual";
    private static final List<String> SUBSCRIPTION_PRODUCT_IDS =
            Arrays.asList(MONTHLY_PRODUCT_ID, ANNUAL_PRODUCT_ID);

    interface Listener {
        void onSubscriptionPriceChanged(String priceLabel);

        void onSubscriptionStatusChanged(boolean active);

        void onBillingMessage(String message);
    }

    private final Context appContext;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final RemoteEntitlementValidator remoteValidator = new RemoteEntitlementValidator();

    private BillingClient billingClient;
    private OfferChoice monthlyOffer;
    private OfferChoice annualOffer;
    private boolean connectionStarted;
    private WeakReference<Activity> pendingPurchaseActivity;
    private String pendingPurchasePlan = PLAN_MONTHLY;

    BillingManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    void startConnection() {
        if (billingClient == null) {
            billingClient = BillingClient.newBuilder(appContext)
                    .setListener(this)
                    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                    .enableAutoServiceReconnection()
                    .build();
        }

        if (billingClient.isReady() || connectionStarted) {
            return;
        }

        connectionStarted = true;
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                connectionStarted = false;
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    querySubscriptionProducts(false);
                    queryActivePurchases(false);
                } else {
                    notifyMessage(appContext.getString(R.string.voice_subscription_unavailable));
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                connectionStarted = false;
            }
        });
    }

    void launchSubscriptionPurchase(Activity activity) {
        launchSubscriptionPurchase(activity, PLAN_MONTHLY);
    }

    void launchSubscriptionPurchase(Activity activity, String plan) {
        if (EntitlementStore.isSubscriptionActive(appContext)) {
            notifyMessage(appContext.getString(R.string.voice_subscription_active));
            return;
        }

        if (billingClient == null || !billingClient.isReady()) {
            pendingPurchaseActivity = new WeakReference<>(activity);
            pendingPurchasePlan = normalizePlan(plan);
            startConnection();
            return;
        }

        OfferChoice offer = offerForPlan(plan);
        if (offer == null || offer.offerToken == null || offer.offerToken.isEmpty()) {
            pendingPurchaseActivity = new WeakReference<>(activity);
            pendingPurchasePlan = normalizePlan(plan);
            querySubscriptionProducts(true);
            return;
        }

        BillingFlowParams.ProductDetailsParams productDetailsParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(offer.productDetails)
                        .setOfferToken(offer.offerToken)
                        .build();

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productDetailsParams))
                .build();

        BillingResult result = billingClient.launchBillingFlow(activity, billingFlowParams);
        pendingPurchaseActivity = null;
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            notifyMessage(appContext.getString(R.string.voice_subscription_unavailable));
        }
    }

    void restorePurchases() {
        notifyMessage(appContext.getString(R.string.voice_subscription_restoring));
        queryActivePurchases(true);
    }

    void refreshPurchases() {
        queryActivePurchases(false);
    }

    void release() {
        if (billingClient != null) {
            billingClient.endConnection();
            billingClient = null;
        }
        remoteValidator.shutdown();
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        int responseCode = billingResult.getResponseCode();
        if (responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases, false);
        } else if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            notifyMessage(appContext.getString(R.string.voice_subscription_canceled));
        } else {
            notifyMessage(appContext.getString(R.string.voice_subscription_unavailable));
        }
    }

    private void querySubscriptionProducts(boolean notifyIfMissing) {
        if (billingClient == null || !billingClient.isReady()) {
            startConnection();
            return;
        }

        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        for (String productId : SUBSCRIPTION_PRODUCT_IDS) {
            productList.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build());
        }

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
            @Override
            public void onProductDetailsResponse(BillingResult billingResult,
                                                 QueryProductDetailsResult productDetailsResult) {
                if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    if (notifyIfMissing) {
                        notifyMessage(appContext.getString(R.string.voice_subscription_unavailable));
                    }
                    return;
                }

                List<ProductDetails> products = productDetailsResult.getProductDetailsList();
                if (products.isEmpty()) {
                    pendingPurchaseActivity = null;
                    if (notifyIfMissing) {
                        notifyMessage(appContext.getString(R.string.voice_subscription_product_missing));
                    }
                    return;
                }

                selectSubscriptionOffers(products);
                launchPendingPurchaseIfReady();
            }
        });
    }

    private void selectSubscriptionOffers(List<ProductDetails> products) {
        monthlyOffer = null;
        annualOffer = null;

        for (ProductDetails productDetails : products) {
            List<ProductDetails.SubscriptionOfferDetails> offers = productDetails.getSubscriptionOfferDetails();
            if (offers == null || offers.isEmpty()) {
                continue;
            }

            for (ProductDetails.SubscriptionOfferDetails offerDetails : offers) {
                ProductDetails.PricingPhase recurringPhase = getRecurringPhase(offerDetails);
                if (recurringPhase == null) {
                    continue;
                }

                OfferChoice offerChoice = new OfferChoice(
                        productDetails,
                        offerDetails.getOfferToken(),
                        recurringPhase.getBillingPeriod(),
                        formatPrice(recurringPhase)
                );

                String productId = productDetails.getProductId();
                if (isAnnualOffer(productId, offerChoice.billingPeriod)) {
                    if (annualOffer == null) {
                        annualOffer = offerChoice;
                    }
                } else if (isMonthlyOffer(productId, offerChoice.billingPeriod) && monthlyOffer == null) {
                    monthlyOffer = offerChoice;
                }
            }
        }

        if (monthlyOffer != null) {
            EntitlementStore.setMonthlyPriceLabel(appContext, monthlyOffer.priceLabel);
        }
        if (annualOffer != null) {
            EntitlementStore.setAnnualPriceLabel(appContext, annualOffer.priceLabel);
        }
        notifyPrice(EntitlementStore.getMonthlyPriceLabel(appContext));
    }

    private void launchPendingPurchaseIfReady() {
        if (pendingPurchaseActivity == null || offerForPlan(pendingPurchasePlan) == null) {
            return;
        }

        Activity activity = pendingPurchaseActivity.get();
        pendingPurchaseActivity = null;
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        String plan = pendingPurchasePlan;
        mainHandler.post(() -> launchSubscriptionPurchase(activity, plan));
    }

    private OfferChoice offerForPlan(String plan) {
        if (PLAN_ANNUAL.equals(normalizePlan(plan))) {
            return annualOffer;
        }
        return monthlyOffer;
    }

    private String normalizePlan(String plan) {
        return PLAN_ANNUAL.equals(plan) ? PLAN_ANNUAL : PLAN_MONTHLY;
    }

    private boolean isMonthlyOffer(String productId, String billingPeriod) {
        return "P1M".equals(billingPeriod) || MONTHLY_PRODUCT_ID.equals(productId);
    }

    private boolean isAnnualOffer(String productId, String billingPeriod) {
        return "P1Y".equals(billingPeriod) || ANNUAL_PRODUCT_ID.equals(productId);
    }

    private ProductDetails.PricingPhase getRecurringPhase(ProductDetails.SubscriptionOfferDetails offerDetails) {
        List<ProductDetails.PricingPhase> phases = offerDetails.getPricingPhases().getPricingPhaseList();
        if (phases == null || phases.isEmpty()) {
            return null;
        }
        return phases.get(phases.size() - 1);
    }

    private String formatPrice(ProductDetails.PricingPhase phase) {
        String period = phase.getBillingPeriod();
        String suffix = "";
        if ("P1M".equals(period)) {
            suffix = "/mês";
        } else if ("P1Y".equals(period)) {
            suffix = "/ano";
        }
        return phase.getFormattedPrice() + suffix;
    }

    private void queryActivePurchases(boolean notifyIfMissing) {
        if (billingClient == null || !billingClient.isReady()) {
            startConnection();
            return;
        }

        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                if (notifyIfMissing) {
                    notifyMessage(appContext.getString(R.string.voice_subscription_unavailable));
                }
                return;
            }
            processPurchases(purchases, notifyIfMissing);
        });
    }

    private void processPurchases(List<Purchase> purchases, boolean notifyIfMissing) {
        boolean wasActive = EntitlementStore.isSubscriptionActive(appContext);
        boolean hasActiveSubscription = false;
        boolean hasPendingSubscription = false;
        Purchase purchaseToValidate = null;
        String productToValidate = "";

        for (Purchase purchase : purchases) {
            String knownProduct = knownSubscriptionProduct(purchase);
            if (knownProduct.isEmpty()) {
                continue;
            }

            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                hasActiveSubscription = true;
                if (purchaseToValidate == null) {
                    purchaseToValidate = purchase;
                    productToValidate = knownProduct;
                }
                acknowledgeIfNeeded(purchase);
            } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                hasPendingSubscription = true;
            }
        }

        if (hasActiveSubscription && remoteValidator.isConfigured() && purchaseToValidate != null) {
            notifyMessage("Confirmando assinatura com segurança.");
            Purchase finalPurchase = purchaseToValidate;
            String finalProduct = productToValidate;
            remoteValidator.validateSubscription(finalProduct, finalPurchase.getPurchaseToken(), active -> mainHandler.post(() -> {
                EntitlementStore.setSubscriptionActive(appContext, active);
                notifySubscriptionStatus(active);
                if (active) {
                    if (!wasActive || notifyIfMissing) {
                        notifyMessage(appContext.getString(R.string.voice_subscription_active));
                    }
                } else if (notifyIfMissing) {
                    notifyMessage(appContext.getString(R.string.voice_subscription_not_found));
                } else {
                    notifyMessage("Não consegui confirmar a assinatura agora.");
                }
            }));
            return;
        }

        EntitlementStore.setSubscriptionActive(appContext, hasActiveSubscription);
        notifySubscriptionStatus(hasActiveSubscription);

        if (hasActiveSubscription) {
            if (!wasActive || notifyIfMissing) {
                notifyMessage(appContext.getString(R.string.voice_subscription_active));
            }
        } else if (hasPendingSubscription) {
            notifyMessage(appContext.getString(R.string.voice_subscription_pending));
        } else if (notifyIfMissing) {
            notifyMessage(appContext.getString(R.string.voice_subscription_not_found));
        }
    }

    private String knownSubscriptionProduct(Purchase purchase) {
        for (String productId : SUBSCRIPTION_PRODUCT_IDS) {
            if (purchase.getProducts().contains(productId)) {
                return productId;
            }
        }
        return "";
    }

    private static final class OfferChoice {
        final ProductDetails productDetails;
        final String offerToken;
        final String billingPeriod;
        final String priceLabel;

        OfferChoice(ProductDetails productDetails, String offerToken, String billingPeriod, String priceLabel) {
            this.productDetails = productDetails;
            this.offerToken = offerToken;
            this.billingPeriod = billingPeriod;
            this.priceLabel = priceLabel;
        }
    }

    private void acknowledgeIfNeeded(Purchase purchase) {
        if (purchase.isAcknowledged()) {
            return;
        }

        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
        billingClient.acknowledgePurchase(params, billingResult -> {
        });
    }

    private void notifyPrice(String priceLabel) {
        mainHandler.post(() -> listener.onSubscriptionPriceChanged(priceLabel));
    }

    private void notifySubscriptionStatus(boolean active) {
        mainHandler.post(() -> listener.onSubscriptionStatusChanged(active));
    }

    private void notifyMessage(String message) {
        mainHandler.post(() -> listener.onBillingMessage(message));
    }
}
