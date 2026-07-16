import express from "express";
import helmet from "helmet";
import rateLimit from "express-rate-limit";

import { getConfig } from "./config.js";
import { JsonStore } from "./storage.js";
import { createStore } from "./firestore-store.js";
import { isGooglePlayConfigured, purchaseTokenHash, verifyGooglePlaySubscription } from "./google-play.js";
import { isValidInstallKey, normalizeUsageKind, USAGE_KINDS } from "./usage.js";
import { summarizeReading } from "./reading.js";

export function createApp(options = {}) {
  const config = options.config || getConfig();
  const store = options.store || createStore(config) || new JsonStore(config.usageStorePath, config.usageRetentionDays);
  const app = express();

  app.disable("x-powered-by");
  if (config.trustProxy) {
    app.set("trust proxy", 1);
  }

  app.use(helmet({ contentSecurityPolicy: false }));
  app.use(express.json({ limit: "96kb" }));
  app.use(rateLimit({
    windowMs: 60 * 1000,
    limit: config.rateLimitPerMinute,
    standardHeaders: "draft-7",
    legacyHeaders: false
  }));

  app.get("/health", (_request, response) => {
    response.json({
      ok: true,
      service: "lepramim-backend",
      googlePlayConfigured: isGooglePlayConfigured(),
      storageDriver: config.storageDriver,
      nodeEnv: config.nodeEnv
    });
  });

  app.post("/v1/free-usage/status", asyncHandler(async (request, response) => {
    const validation = validateUsageRequest(request.body, config);
    if (!validation.ok) {
      return response.status(400).json(validation.body);
    }

    return response.json(await store.getUsageStatus(
      validation.installKey,
      validation.kind,
      validation.limit
    ));
  }));

  app.post("/v1/free-usage/consume", asyncHandler(async (request, response) => {
    const validation = validateUsageRequest(request.body, config);
    if (!validation.ok) {
      return response.status(400).json(validation.body);
    }

    const result = await store.consumeUsage(
      validation.installKey,
      validation.kind,
      validation.limit
    );
    return response.json(result);
  }));

  app.post("/v1/entitlements/verify-subscription", asyncHandler(async (request, response) => {
    const { productId, purchaseToken } = request.body || {};
    if (!isValidProduct(productId, config.allowedProductIds) || !isValidPurchaseToken(purchaseToken)) {
      return response.status(400).json({
        active: false,
        reason: "productId ou purchaseToken invalido."
      });
    }

    if (!isGooglePlayConfigured()) {
      return response.status(501).json({
        active: false,
        reason: "Servidor sem credenciais da Google Play Developer API."
      });
    }

    try {
      const result = await verifyGooglePlaySubscription({
        packageName: config.packageName,
        productId,
        purchaseToken,
        allowedProductIds: config.allowedProductIds
      });

      const checkedAt = new Date().toISOString();
      await store.recordEntitlement(purchaseTokenHash(purchaseToken), {
        productId,
        active: result.active,
        subscriptionState: result.subscriptionState,
        expiryTime: result.expiryTime,
        checkedAt
      });

      return response.json({
        ...result,
        checkedAt
      });
    } catch (error) {
      console.warn("Google Play validation failed", {
        code: error?.code || "UNKNOWN",
        status: error?.status || error?.response?.status || "UNKNOWN"
      });
      return response.status(502).json({
        active: false,
        reason: "Falha ao consultar Google Play Developer API.",
        code: error?.code || "UNKNOWN"
      });
    }
  }));

  app.post("/v1/reading/summarize", asyncHandler(async (request, response) => {
    const { text, context = "tela", mode = "simple" } = request.body || {};
    const result = await summarizeReading({
      text,
      context,
      mode,
      geminiApiKey: config.geminiApiKey
    });
    return response.status(result.ok ? 200 : 400).json(result);
  }));

  app.use((_request, response) => {
    response.status(404).json({ ok: false, reason: "Endpoint nao encontrado." });
  });

  app.use((error, _request, response, _next) => {
    console.error("Unhandled server error", error);
    response.status(500).json({ ok: false, reason: "Falha interna do servidor." });
  });

  return app;
}

function asyncHandler(handler) {
  return (request, response, next) => {
    Promise.resolve(handler(request, response, next)).catch(next);
  };
}

function validateUsageRequest(body, config) {
  const installKey = body?.installKey;
  const kind = normalizeUsageKind(body?.kind);

  if (!isValidInstallKey(installKey) || !USAGE_KINDS.has(kind)) {
    return {
      ok: false,
      body: {
        allowed: false,
        remaining: 0,
        reason: "installKey ou kind invalido."
      }
    };
  }

  return {
    ok: true,
    installKey,
    kind,
    limit: config.freeLimits[kind]
  };
}

function isValidProduct(productId, allowedProductIds) {
  return typeof productId === "string" && allowedProductIds.includes(productId);
}

function isValidPurchaseToken(purchaseToken) {
  return typeof purchaseToken === "string" && purchaseToken.length >= 20 && purchaseToken.length <= 4096;
}
