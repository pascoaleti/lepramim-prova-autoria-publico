package com.lepramim.app;

import android.content.Context;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class RemoteFreeUsageLimiter {
    static final String KIND_SCREEN = "screen";
    static final String KIND_IMAGE = "image";

    private static final int TIMEOUT_MS = 1400;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private RemoteFreeUsageLimiter() {
    }

    interface Callback {
        void onResult(UsageResult result);
    }

    static final class UsageResult {
        final boolean available;
        final boolean allowed;
        final int remaining;
        final int limit;
        final String day;

        private UsageResult(boolean available, boolean allowed, int remaining, int limit, String day) {
            this.available = available;
            this.allowed = allowed;
            this.remaining = remaining;
            this.limit = limit;
            this.day = day == null ? "" : day;
        }

        static UsageResult unavailable() {
            return new UsageResult(false, false, -1, -1, "");
        }

        static UsageResult denied() {
            return new UsageResult(true, false, 0, -1, "");
        }
    }

    static UsageResult tryConsume(Context context, String kind) {
        if (!isConfigured()) {
            return null;
        }

        Future<UsageResult> future = EXECUTOR.submit(new RequestTask(context.getApplicationContext(), kind, true));
        try {
            return future.get(TIMEOUT_MS + 300L, TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            future.cancel(true);
            return UsageResult.denied();
        }
    }

    static void fetchStatusAsync(Context context, String kind, Callback callback) {
        if (!isConfigured()) {
            if (callback != null) {
                callback.onResult(UsageResult.unavailable());
            }
            return;
        }
        EXECUTOR.execute(() -> {
            UsageResult result;
            try {
                result = new RequestTask(context.getApplicationContext(), kind, false).call();
            } catch (Exception exception) {
                result = UsageResult.unavailable();
            }
            if (callback != null) {
                callback.onResult(result);
            }
        });
    }

    static boolean isConfigured() {
        return BuildConfig.ENTITLEMENT_BASE_URL != null
                && !BuildConfig.ENTITLEMENT_BASE_URL.trim().isEmpty();
    }

    private static final class RequestTask implements Callable<UsageResult> {
        private final Context context;
        private final String kind;
        private final boolean consume;

        RequestTask(Context context, String kind, boolean consume) {
            this.context = context;
            this.kind = kind == null ? "" : kind;
            this.consume = consume;
        }

        @Override
        public UsageResult call() {
            HttpURLConnection connection = null;
            try {
                String endpoint = consume ? "/v1/free-usage/consume" : "/v1/free-usage/status";
                URL url = new URL(BuildConfig.ENTITLEMENT_BASE_URL + endpoint);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                JSONObject payload = new JSONObject();
                payload.put("installKey", deviceUsageKey(context));
                payload.put("kind", kind);
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(body);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    return consume ? UsageResult.denied() : UsageResult.unavailable();
                }

                String response = readResponse(connection);
                JSONObject json = new JSONObject(response);
                return new UsageResult(
                        true,
                        json.optBoolean("allowed", false),
                        json.optInt("remaining", -1),
                        json.optInt("limit", -1),
                        json.optString("day", "")
                );
            } catch (Exception exception) {
                return consume ? UsageResult.denied() : UsageResult.unavailable();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    private static String readResponse(HttpURLConnection connection) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static String deviceUsageKey(Context context) throws Exception {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String raw = "lepramim:v1:" + context.getPackageName() + ":" + (androidId == null ? "" : androidId);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) {
            builder.append(String.format(Locale.US, "%02x", value));
        }
        return builder.toString();
    }
}
