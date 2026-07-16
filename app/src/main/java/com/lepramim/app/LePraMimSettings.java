package com.lepramim.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class LePraMimSettings {
    private static final String PREFS_NAME = "lepramim_settings";

    private static final String KEY_ONBOARDING_DONE = "onboarding_done";
    private static final String KEY_READING_MODE = "reading_mode";
    private static final String KEY_SAFE_MODE = "safe_mode";
    private static final String KEY_AUTO_READ = "auto_read";
    private static final String KEY_OVERLAY_ENABLED = "overlay_enabled";
    private static final String KEY_OVERLAY_SIZE = "overlay_size";
    private static final String KEY_OVERLAY_DISCREET = "overlay_discreet";
    private static final String KEY_OVERLAY_X = "overlay_x";
    private static final String KEY_OVERLAY_Y = "overlay_y";
    private static final String KEY_SPEECH_RATE = "speech_rate";
    private static final String KEY_SPEECH_PITCH = "speech_pitch";
    private static final String KEY_BLOCKED_PACKAGES = "blocked_packages";
    private static final String BLOCKED_PACKAGES_NONE = "__none__";

    static final String OVERLAY_SMALL = "small";
    static final String OVERLAY_MEDIUM = "medium";
    static final String OVERLAY_LARGE = "large";

    private static final String[] DEFAULT_SENSITIVE_PACKAGES = {
            "br.com.itau",
            "com.itau",
            "br.com.bradesco",
            "br.com.bb",
            "br.com.caixa",
            "br.com.santander",
            "com.nu",
            "com.picpay",
            "com.mercadopago",
            "com.authy",
            "com.google.android.apps.authenticator2"
    };

    private LePraMimSettings() {
    }

    static boolean isOnboardingDone(Context context) {
        return prefs(context).getBoolean(KEY_ONBOARDING_DONE, false);
    }

    static void setOnboardingDone(Context context, boolean done) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply();
    }

    static ReadingMode getReadingMode(Context context) {
        String stored = prefs(context).getString(KEY_READING_MODE, ReadingMode.IMPORTANT_ONLY.name());
        try {
            return ReadingMode.valueOf(stored);
        } catch (IllegalArgumentException exception) {
            return ReadingMode.IMPORTANT_ONLY;
        }
    }

    static void setReadingMode(Context context, ReadingMode mode) {
        prefs(context).edit().putString(KEY_READING_MODE, mode.name()).apply();
    }

    static boolean isSafeModeEnabled(Context context) {
        return prefs(context).getBoolean(KEY_SAFE_MODE, true);
    }

    static void setSafeModeEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SAFE_MODE, enabled).apply();
    }

    static boolean isAutoReadEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_READ, false);
    }

    static void setAutoReadEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_READ, enabled).apply();
    }

    static boolean isOverlayEnabled(Context context) {
        return prefs(context).getBoolean(KEY_OVERLAY_ENABLED, true);
    }

    static void setOverlayEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply();
    }

    static String getOverlaySize(Context context) {
        return prefs(context).getString(KEY_OVERLAY_SIZE, OVERLAY_MEDIUM);
    }

    static void setOverlaySize(Context context, String size) {
        String normalized = OVERLAY_MEDIUM;
        if (OVERLAY_SMALL.equals(size) || OVERLAY_LARGE.equals(size)) {
            normalized = size;
        }
        prefs(context).edit().putString(KEY_OVERLAY_SIZE, normalized).apply();
    }

    static int getOverlaySizeDp(Context context) {
        String size = getOverlaySize(context);
        if (OVERLAY_SMALL.equals(size)) {
            return 64;
        }
        if (OVERLAY_LARGE.equals(size)) {
            return 94;
        }
        return 78;
    }

    static boolean isOverlayDiscreet(Context context) {
        return prefs(context).getBoolean(KEY_OVERLAY_DISCREET, false);
    }

    static void setOverlayDiscreet(Context context, boolean discreet) {
        prefs(context).edit().putBoolean(KEY_OVERLAY_DISCREET, discreet).apply();
    }

    static int getOverlayX(Context context, int fallback) {
        return prefs(context).getInt(KEY_OVERLAY_X, fallback);
    }

    static int getOverlayY(Context context, int fallback) {
        return prefs(context).getInt(KEY_OVERLAY_Y, fallback);
    }

    static void setOverlayPosition(Context context, int x, int y) {
        prefs(context).edit()
                .putInt(KEY_OVERLAY_X, Math.max(0, x))
                .putInt(KEY_OVERLAY_Y, Math.max(0, y))
                .apply();
    }

    static float getSpeechRate(Context context) {
        return prefs(context).getFloat(KEY_SPEECH_RATE, 0.88f);
    }

    static float getSpeechPitch(Context context) {
        return prefs(context).getFloat(KEY_SPEECH_PITCH, 1.03f);
    }

    static void setSpeechPreset(Context context, String preset) {
        float rate;
        if ("muito_devagar".equals(preset)) {
            rate = 0.68f;
        } else if ("devagar".equals(preset)) {
            rate = 0.82f;
        } else if ("rapido".equals(preset)) {
            rate = 1.08f;
        } else {
            rate = 0.88f;
        }
        prefs(context).edit().putFloat(KEY_SPEECH_RATE, rate).apply();
    }

    static void setSpeechPitch(Context context, float pitch) {
        float normalized = Math.max(0.75f, Math.min(1.25f, pitch));
        prefs(context).edit().putFloat(KEY_SPEECH_PITCH, normalized).apply();
    }

    static boolean isPackageBlocked(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        String normalized = packageName.toLowerCase(Locale.US);
        Set<String> blocked = getBlockedPackages(context);
        for (String prefix : blocked) {
            if (!prefix.isEmpty() && normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    static Set<String> getBlockedPackages(Context context) {
        String stored = prefs(context).getString(KEY_BLOCKED_PACKAGES, "");
        Set<String> blocked = new HashSet<>();
        if (BLOCKED_PACKAGES_NONE.equals(stored)) {
            return blocked;
        }
        if (stored == null || stored.trim().isEmpty()) {
            blocked.addAll(Arrays.asList(DEFAULT_SENSITIVE_PACKAGES));
            return blocked;
        }
        for (String item : stored.split(",")) {
            String normalized = item.trim().toLowerCase(Locale.US);
            if (!normalized.isEmpty()) {
                blocked.add(normalized);
            }
        }
        return blocked;
    }

    static void setDefaultSensitivePackagesBlocked(Context context, boolean blocked) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (blocked) {
            editor.remove(KEY_BLOCKED_PACKAGES);
        } else {
            editor.putString(KEY_BLOCKED_PACKAGES, BLOCKED_PACKAGES_NONE);
        }
        editor.apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
