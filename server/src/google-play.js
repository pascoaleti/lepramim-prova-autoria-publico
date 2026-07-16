import { createHash } from "node:crypto";
import { google } from "googleapis";

const ACTIVE_STATES = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"
]);

const ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher";

export function isGooglePlayConfigured() {
  return Boolean(
    process.env.GOOGLE_APPLICATION_CREDENTIALS ||
      process.env.GOOGLE_SERVICE_ACCOUNT_JSON ||
      process.env.GOOGLE_SERVICE_ACCOUNT_JSON_BASE64 ||
      process.env.GOOGLE_OAUTH_CREDENTIALS_JSON ||
      process.env.GOOGLE_OAUTH_CREDENTIALS_JSON_BASE64 ||
      (
        process.env.GOOGLE_OAUTH_CLIENT_ID &&
        process.env.GOOGLE_OAUTH_CLIENT_SECRET &&
        process.env.GOOGLE_OAUTH_REFRESH_TOKEN
      )
  );
}

export async function verifyGooglePlaySubscription({ packageName, productId, purchaseToken, allowedProductIds }) {
  if (!allowedProductIds.includes(productId)) {
    return {
      active: false,
      productId,
      subscriptionState: "PRODUCT_NOT_ALLOWED",
      expiryTime: null
    };
  }

  const auth = createGoogleAuth();
  const androidpublisher = google.androidpublisher({
    version: "v3",
    auth
  });

  const result = await androidpublisher.purchases.subscriptionsv2.get({
    packageName,
    token: purchaseToken
  });

  const subscription = result.data || {};
  const lineItems = Array.isArray(subscription.lineItems) ? subscription.lineItems : [];
  const ownsProduct = lineItems.some((item) => item.productId === productId);
  const subscriptionState = subscription.subscriptionState || "UNKNOWN";

  return {
    active: ownsProduct && ACTIVE_STATES.has(subscriptionState),
    productId,
    subscriptionState,
    expiryTime: lineItems[0]?.expiryTime || null
  };
}

export function purchaseTokenHash(purchaseToken) {
  return createHash("sha256").update(String(purchaseToken || ""), "utf8").digest("hex");
}

function createGoogleAuth() {
  const oauthCredentials = readOAuthCredentials();
  if (oauthCredentials) {
    const oauth = new google.auth.OAuth2(
      oauthCredentials.clientId,
      oauthCredentials.clientSecret,
      oauthCredentials.redirectUri || "urn:ietf:wg:oauth:2.0:oob"
    );
    oauth.setCredentials({
      refresh_token: oauthCredentials.refreshToken,
      scope: ANDROID_PUBLISHER_SCOPE
    });
    return oauth;
  }

  const credentials = readServiceAccountCredentials();
  if (credentials) {
    return new google.auth.GoogleAuth({
      credentials,
      scopes: [ANDROID_PUBLISHER_SCOPE]
    });
  }

  return new google.auth.GoogleAuth({
    scopes: [ANDROID_PUBLISHER_SCOPE]
  });
}

function readServiceAccountCredentials() {
  if (process.env.GOOGLE_SERVICE_ACCOUNT_JSON_BASE64) {
    return JSON.parse(Buffer.from(process.env.GOOGLE_SERVICE_ACCOUNT_JSON_BASE64, "base64").toString("utf8"));
  }
  if (process.env.GOOGLE_SERVICE_ACCOUNT_JSON) {
    return JSON.parse(process.env.GOOGLE_SERVICE_ACCOUNT_JSON);
  }
  return null;
}

function readOAuthCredentials() {
  const json = readOAuthCredentialsJson();
  if (json) {
    const client = json.installed || json.web || json.client || json;
    const token = json.token || json.credentials || json;
    return normalizeOAuthCredentials({
      clientId: client.client_id || client.clientId,
      clientSecret: client.client_secret || client.clientSecret,
      refreshToken: token.refresh_token || token.refreshToken,
      redirectUri: Array.isArray(client.redirect_uris) ? client.redirect_uris[0] : client.redirectUri
    });
  }

  return normalizeOAuthCredentials({
    clientId: process.env.GOOGLE_OAUTH_CLIENT_ID,
    clientSecret: process.env.GOOGLE_OAUTH_CLIENT_SECRET,
    refreshToken: process.env.GOOGLE_OAUTH_REFRESH_TOKEN,
    redirectUri: process.env.GOOGLE_OAUTH_REDIRECT_URI
  });
}

function readOAuthCredentialsJson() {
  if (process.env.GOOGLE_OAUTH_CREDENTIALS_JSON_BASE64) {
    return JSON.parse(Buffer.from(process.env.GOOGLE_OAUTH_CREDENTIALS_JSON_BASE64, "base64").toString("utf8"));
  }
  if (process.env.GOOGLE_OAUTH_CREDENTIALS_JSON) {
    return JSON.parse(process.env.GOOGLE_OAUTH_CREDENTIALS_JSON);
  }
  return null;
}

function normalizeOAuthCredentials(credentials) {
  if (!credentials?.clientId || !credentials?.clientSecret || !credentials?.refreshToken) {
    return null;
  }
  return credentials;
}
