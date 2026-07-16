package com.lepramim.app;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RemoteEntitlementValidator {
    interface Callback {
        void onResult(boolean active);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    boolean isConfigured() {
        return BuildConfig.ENTITLEMENT_BASE_URL != null
                && !BuildConfig.ENTITLEMENT_BASE_URL.trim().isEmpty();
    }

    void validateSubscription(String productId, String purchaseToken, Callback callback) {
        if (!isConfigured()) {
            callback.onResult(true);
            return;
        }

        executor.execute(() -> {
            boolean active = false;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BuildConfig.ENTITLEMENT_BASE_URL + "/v1/entitlements/verify-subscription");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(9000);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setDoOutput(true);

                String body = "{\"productId\":\"" + escape(productId) + "\",\"purchaseToken\":\"" + escape(purchaseToken) + "\"}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(bytes);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    String response = readResponse(connection);
                    active = response.contains("\"active\":true");
                } else if (responseCode == 501 || responseCode == 502 || responseCode == 503 || responseCode == 504) {
                    active = true;
                }
            } catch (Exception ignored) {
                active = true;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            callback.onResult(active);
        });
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
