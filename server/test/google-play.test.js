import test from "node:test";
import assert from "node:assert/strict";

import { isGooglePlayConfigured } from "../src/google-play.js";

test("Google Play configuration accepts OAuth refresh token credentials", () => {
  const previous = snapshotEnv();
  try {
    clearGooglePlayEnv();
    assert.equal(isGooglePlayConfigured(), false);

    process.env.GOOGLE_OAUTH_CLIENT_ID = "client-id";
    process.env.GOOGLE_OAUTH_CLIENT_SECRET = "client-secret";
    process.env.GOOGLE_OAUTH_REFRESH_TOKEN = "refresh-token";

    assert.equal(isGooglePlayConfigured(), true);
  } finally {
    restoreEnv(previous);
  }
});

function snapshotEnv() {
  return {
    GOOGLE_APPLICATION_CREDENTIALS: process.env.GOOGLE_APPLICATION_CREDENTIALS,
    GOOGLE_SERVICE_ACCOUNT_JSON: process.env.GOOGLE_SERVICE_ACCOUNT_JSON,
    GOOGLE_SERVICE_ACCOUNT_JSON_BASE64: process.env.GOOGLE_SERVICE_ACCOUNT_JSON_BASE64,
    GOOGLE_OAUTH_CREDENTIALS_JSON: process.env.GOOGLE_OAUTH_CREDENTIALS_JSON,
    GOOGLE_OAUTH_CREDENTIALS_JSON_BASE64: process.env.GOOGLE_OAUTH_CREDENTIALS_JSON_BASE64,
    GOOGLE_OAUTH_CLIENT_ID: process.env.GOOGLE_OAUTH_CLIENT_ID,
    GOOGLE_OAUTH_CLIENT_SECRET: process.env.GOOGLE_OAUTH_CLIENT_SECRET,
    GOOGLE_OAUTH_REFRESH_TOKEN: process.env.GOOGLE_OAUTH_REFRESH_TOKEN,
    GOOGLE_OAUTH_REDIRECT_URI: process.env.GOOGLE_OAUTH_REDIRECT_URI
  };
}

function clearGooglePlayEnv() {
  for (const key of Object.keys(snapshotEnv())) {
    delete process.env[key];
  }
}

function restoreEnv(previous) {
  clearGooglePlayEnv();
  for (const [key, value] of Object.entries(previous)) {
    if (value !== undefined) {
      process.env[key] = value;
    }
  }
}
