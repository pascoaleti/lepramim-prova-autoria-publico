import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { createApp } from "../src/app.js";

const INSTALL_KEY = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

test("free usage blocks after daily limit and status does not consume", async () => {
  const tempDir = await mkdtemp(join(tmpdir(), "lepramim-backend-"));
  const app = createApp({
    config: {
      port: 0,
      nodeEnv: "test",
      trustProxy: false,
      packageName: "com.lepramim.app",
      allowedProductIds: ["lepramim_plus_monthly", "lepramim_plus_annual"],
      usageStorePath: join(tempDir, "usage.json"),
      usageRetentionDays: 7,
      freeLimits: { screen: 2, image: 1 },
      rateLimitPerMinute: 1000,
      geminiApiKey: ""
    }
  });

  const server = app.listen(0);
  try {
    const baseUrl = `http://127.0.0.1:${server.address().port}`;

    const initial = await postJson(`${baseUrl}/v1/free-usage/status`, { installKey: INSTALL_KEY, kind: "screen" });
    assert.equal(initial.allowed, true);
    assert.equal(initial.remaining, 2);

    const first = await postJson(`${baseUrl}/v1/free-usage/consume`, { installKey: INSTALL_KEY, kind: "screen" });
    assert.equal(first.allowed, true);
    assert.equal(first.remaining, 1);

    const second = await postJson(`${baseUrl}/v1/free-usage/consume`, { installKey: INSTALL_KEY, kind: "screen" });
    assert.equal(second.allowed, true);
    assert.equal(second.remaining, 0);

    const blocked = await postJson(`${baseUrl}/v1/free-usage/consume`, { installKey: INSTALL_KEY, kind: "screen" });
    assert.equal(blocked.allowed, false);
    assert.equal(blocked.remaining, 0);
  } finally {
    await new Promise((resolve) => server.close(resolve));
    await rm(tempDir, { recursive: true, force: true });
  }
});

test("subscription validation requires Google Play credentials", async () => {
  const tempDir = await mkdtemp(join(tmpdir(), "lepramim-backend-"));
  const app = createApp({
    config: {
      port: 0,
      nodeEnv: "test",
      trustProxy: false,
      packageName: "com.lepramim.app",
      allowedProductIds: ["lepramim_plus_monthly", "lepramim_plus_annual"],
      usageStorePath: join(tempDir, "usage.json"),
      usageRetentionDays: 7,
      freeLimits: { screen: 2, image: 1 },
      rateLimitPerMinute: 1000,
      geminiApiKey: ""
    }
  });

  const server = app.listen(0);
  try {
    const baseUrl = `http://127.0.0.1:${server.address().port}`;
    const response = await fetch(`${baseUrl}/v1/entitlements/verify-subscription`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        productId: "lepramim_plus_monthly",
        purchaseToken: "this-is-a-valid-length-test-token"
      })
    });

    assert.equal(response.status, 501);
    const body = await response.json();
    assert.equal(body.active, false);
  } finally {
    await new Promise((resolve) => server.close(resolve));
    await rm(tempDir, { recursive: true, force: true });
  }
});

async function postJson(url, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  assert.equal(response.status, 200);
  return response.json();
}
