const DEFAULT_PRODUCTS = "lepramim_plus_monthly,lepramim_plus_annual";

export function getConfig() {
  return {
    port: readNumber("PORT", 8080),
    nodeEnv: process.env.NODE_ENV || "development",
    trustProxy: readBoolean("TRUST_PROXY", false),
    packageName: process.env.ANDROID_PACKAGE_NAME || "com.lepramim.app",
    allowedProductIds: readList("ALLOWED_PRODUCT_IDS", DEFAULT_PRODUCTS),
    storageDriver: String(process.env.STORAGE_DRIVER || "file").trim().toLowerCase(),
    firestoreUsageCollection: process.env.FIRESTORE_USAGE_COLLECTION || "lepramim_free_usage",
    firestoreEntitlementsCollection: process.env.FIRESTORE_ENTITLEMENTS_COLLECTION || "lepramim_entitlements",
    usageStorePath: process.env.FREE_USAGE_STORE_PATH || "./data/free-usage.json",
    usageRetentionDays: readNumber("FREE_USAGE_RETENTION_DAYS", 45),
    freeLimits: {
      screen: readNumber("FREE_SCREEN_READS_PER_DAY", 12),
      image: readNumber("FREE_IMAGE_READS_PER_DAY", 3)
    },
    rateLimitPerMinute: readNumber("RATE_LIMIT_PER_MINUTE", 90),
    geminiApiKey: process.env.GEMINI_API_KEY || ""
  };
}

function readNumber(name, fallback) {
  const value = Number(process.env[name]);
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

function readBoolean(name, fallback) {
  const value = String(process.env[name] || "").trim().toLowerCase();
  if (["1", "true", "yes", "sim"].includes(value)) {
    return true;
  }
  if (["0", "false", "no", "nao", "não"].includes(value)) {
    return false;
  }
  return fallback;
}

function readList(name, fallback) {
  return String(process.env[name] || fallback)
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}
