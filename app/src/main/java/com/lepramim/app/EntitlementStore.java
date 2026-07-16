package com.lepramim.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class EntitlementStore {
    private static final String PREFS_NAME = "lepramim_entitlements";
    private static final String KEY_SUBSCRIPTION_ACTIVE = "subscription_active";
    private static final String KEY_PRICE_LABEL = "price_label";
    private static final String KEY_ANNUAL_PRICE_LABEL = "annual_price_label";
    private static final String KEY_USAGE_DAY = "usage_day";
    private static final String KEY_SCREEN_READS_USED = "screen_reads_used";
    private static final String KEY_IMAGE_READS_USED = "image_reads_used";
    private static final String KEY_REMOTE_USAGE_DAY = "remote_usage_day";
    private static final String KEY_REMOTE_SCREEN_REMAINING = "remote_screen_remaining";
    private static final String KEY_REMOTE_IMAGE_REMAINING = "remote_image_remaining";
    private static final String FALLBACK_MONTHLY_PRICE_LABEL = "R$ 9,90/m\u00eas";
    private static final String FALLBACK_ANNUAL_PRICE_LABEL = "R$ 69,90/ano";

    private EntitlementStore() {
    }

    static boolean hasAccess(Context context) {
        return hasUnlimitedAccess(context);
    }

    static boolean hasUnlimitedAccess(Context context) {
        return BuildConfig.DEBUG || isSubscriptionActive(context);
    }

    static boolean isSubscriptionActive(Context context) {
        return prefs(context).getBoolean(KEY_SUBSCRIPTION_ACTIVE, false);
    }

    static void setSubscriptionActive(Context context, boolean active) {
        prefs(context).edit()
                .putBoolean(KEY_SUBSCRIPTION_ACTIVE, active)
                .apply();
    }

    static String getPriceLabel(Context context) {
        return getMonthlyPriceLabel(context);
    }

    static void setPriceLabel(Context context, String priceLabel) {
        setMonthlyPriceLabel(context, priceLabel);
    }

    static String getMonthlyPriceLabel(Context context) {
        return prefs(context).getString(KEY_PRICE_LABEL, FALLBACK_MONTHLY_PRICE_LABEL);
    }

    static void setMonthlyPriceLabel(Context context, String priceLabel) {
        if (priceLabel == null || priceLabel.trim().isEmpty()) {
            return;
        }
        prefs(context).edit()
                .putString(KEY_PRICE_LABEL, priceLabel)
                .apply();
    }

    static String getAnnualPriceLabel(Context context) {
        return prefs(context).getString(KEY_ANNUAL_PRICE_LABEL, FALLBACK_ANNUAL_PRICE_LABEL);
    }

    static void setAnnualPriceLabel(Context context, String priceLabel) {
        if (priceLabel == null || priceLabel.trim().isEmpty()) {
            return;
        }
        prefs(context).edit()
                .putString(KEY_ANNUAL_PRICE_LABEL, priceLabel)
                .apply();
    }

    static boolean tryConsumeScreenRead(Context context) {
        if (hasUnlimitedAccess(context)) {
            return true;
        }
        RemoteFreeUsageLimiter.UsageResult remoteResult =
                RemoteFreeUsageLimiter.tryConsume(context, RemoteFreeUsageLimiter.KIND_SCREEN);
        if (remoteResult != null) {
            rememberRemoteUsage(context, RemoteFreeUsageLimiter.KIND_SCREEN, remoteResult);
            return remoteResult.allowed;
        }
        SharedPreferences preferences = prefs(context);
        ensureToday(preferences);
        int used = preferences.getInt(KEY_SCREEN_READS_USED, 0);
        if (!FreePlanPolicy.canConsume(used, FreePlanPolicy.FREE_SCREEN_READS_PER_DAY)) {
            return false;
        }
        preferences.edit()
                .putInt(KEY_SCREEN_READS_USED, used + 1)
                .apply();
        return true;
    }

    static boolean tryConsumeImageRead(Context context) {
        if (hasUnlimitedAccess(context)) {
            return true;
        }
        RemoteFreeUsageLimiter.UsageResult remoteResult =
                RemoteFreeUsageLimiter.tryConsume(context, RemoteFreeUsageLimiter.KIND_IMAGE);
        if (remoteResult != null) {
            rememberRemoteUsage(context, RemoteFreeUsageLimiter.KIND_IMAGE, remoteResult);
            return remoteResult.allowed;
        }
        SharedPreferences preferences = prefs(context);
        ensureToday(preferences);
        int used = preferences.getInt(KEY_IMAGE_READS_USED, 0);
        if (!FreePlanPolicy.canConsume(used, FreePlanPolicy.FREE_IMAGE_READS_PER_DAY)) {
            return false;
        }
        preferences.edit()
                .putInt(KEY_IMAGE_READS_USED, used + 1)
                .apply();
        return true;
    }

    static int getRemainingScreenReads(Context context) {
        if (hasUnlimitedAccess(context)) {
            return Integer.MAX_VALUE;
        }
        Integer remoteRemaining = getCachedRemoteRemaining(context, RemoteFreeUsageLimiter.KIND_SCREEN);
        if (remoteRemaining != null) {
            return remoteRemaining;
        }
        SharedPreferences preferences = prefs(context);
        ensureToday(preferences);
        return FreePlanPolicy.remaining(preferences.getInt(KEY_SCREEN_READS_USED, 0), FreePlanPolicy.FREE_SCREEN_READS_PER_DAY);
    }

    static int getRemainingImageReads(Context context) {
        if (hasUnlimitedAccess(context)) {
            return Integer.MAX_VALUE;
        }
        Integer remoteRemaining = getCachedRemoteRemaining(context, RemoteFreeUsageLimiter.KIND_IMAGE);
        if (remoteRemaining != null) {
            return remoteRemaining;
        }
        SharedPreferences preferences = prefs(context);
        ensureToday(preferences);
        return FreePlanPolicy.remaining(preferences.getInt(KEY_IMAGE_READS_USED, 0), FreePlanPolicy.FREE_IMAGE_READS_PER_DAY);
    }

    static String getPlanLabel(Context context) {
        if (hasUnlimitedAccess(context)) {
            return "Plus ativo: uso sem limite";
        }
        return "Gr\u00e1tis: " + getRemainingScreenReads(context) + " leituras hoje";
    }

    static void refreshRemoteUsageAsync(Context context, Runnable onComplete) {
        if (!RemoteFreeUsageLimiter.isConfigured() || hasUnlimitedAccess(context)) {
            runOnMain(onComplete);
            return;
        }
        Context appContext = context.getApplicationContext();
        RemoteFreeUsageLimiter.fetchStatusAsync(appContext, RemoteFreeUsageLimiter.KIND_SCREEN, result -> {
            rememberRemoteUsage(appContext, RemoteFreeUsageLimiter.KIND_SCREEN, result);
            runOnMain(onComplete);
        });
    }

    static String getScreenLimitReachedMessage(Context context) {
        return "As leituras gr\u00e1tis de hoje acabaram. Abra o LePraMim e toque em Plus para usar sem limite.";
    }

    static String getImageLimitReachedMessage(Context context) {
        return "As leituras gr\u00e1tis de foto de hoje acabaram. Toque em Plus para usar sem limite.";
    }

    static String getImageRemainingMessage(Context context) {
        if (hasUnlimitedAccess(context)) {
            return "";
        }
        int remaining = getRemainingImageReads(context);
        if (remaining == 1) {
            return "Voc\u00ea ainda tem 1 leitura de foto gr\u00e1tis hoje.";
        }
        return "Voc\u00ea ainda tem " + remaining + " leituras de foto gr\u00e1tis hoje.";
    }

    private static void ensureToday(SharedPreferences preferences) {
        int today = currentDayKey();
        if (preferences.getInt(KEY_USAGE_DAY, -1) == today) {
            return;
        }
        preferences.edit()
                .putInt(KEY_USAGE_DAY, today)
                .putInt(KEY_SCREEN_READS_USED, 0)
                .putInt(KEY_IMAGE_READS_USED, 0)
                .apply();
    }

    private static int currentDayKey() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void rememberRemoteUsage(Context context, String kind, RemoteFreeUsageLimiter.UsageResult result) {
        if (result == null || !result.available || result.remaining < 0) {
            return;
        }
        String day = result.day == null || result.day.isEmpty() ? currentRemoteDayKey() : result.day;
        String remainingKey = RemoteFreeUsageLimiter.KIND_IMAGE.equals(kind)
                ? KEY_REMOTE_IMAGE_REMAINING
                : KEY_REMOTE_SCREEN_REMAINING;
        prefs(context).edit()
                .putString(KEY_REMOTE_USAGE_DAY, day)
                .putInt(remainingKey, result.remaining)
                .apply();
    }

    private static Integer getCachedRemoteRemaining(Context context, String kind) {
        if (!RemoteFreeUsageLimiter.isConfigured()) {
            return null;
        }
        SharedPreferences preferences = prefs(context);
        if (!currentRemoteDayKey().equals(preferences.getString(KEY_REMOTE_USAGE_DAY, ""))) {
            return null;
        }
        String remainingKey = RemoteFreeUsageLimiter.KIND_IMAGE.equals(kind)
                ? KEY_REMOTE_IMAGE_REMAINING
                : KEY_REMOTE_SCREEN_REMAINING;
        if (!preferences.contains(remainingKey)) {
            return null;
        }
        return Math.max(0, preferences.getInt(remainingKey, 0));
    }

    private static String currentRemoteDayKey() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(new Date());
    }

    private static void runOnMain(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(runnable);
    }
}
