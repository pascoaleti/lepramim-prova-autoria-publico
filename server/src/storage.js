import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { consumeUsage, getUsageStatus } from "./usage.js";

export class JsonStore {
  constructor(filePath, retentionDays = 45) {
    this.filePath = filePath;
    this.retentionDays = retentionDays;
    this.queue = Promise.resolve();
  }

  async snapshot() {
    return normalizeStore(await this.read());
  }

  async getUsageStatus(installKey, kind, limit) {
    return getUsageStatus(await this.snapshot(), installKey, kind, limit);
  }

  async consumeUsage(installKey, kind, limit) {
    return this.mutate((data) => consumeUsage(data, installKey, kind, limit));
  }

  async recordEntitlement(tokenHash, entitlement) {
    return this.mutate((data) => {
      data.entitlements[tokenHash] = entitlement;
      return null;
    });
  }

  async mutate(mutator) {
    const run = async () => {
      const store = normalizeStore(await this.read());
      const result = await mutator(store);
      pruneUsage(store, this.retentionDays);
      await this.write(store);
      return result;
    };

    this.queue = this.queue.catch(() => undefined).then(run);
    return this.queue;
  }

  async read() {
    try {
      return JSON.parse(await readFile(this.filePath, "utf8"));
    } catch {
      return createEmptyStore();
    }
  }

  async write(store) {
    await mkdir(dirname(this.filePath), { recursive: true });
    const tempPath = `${this.filePath}.${process.pid}.${Date.now()}.tmp`;
    await writeFile(tempPath, JSON.stringify(store, null, 2), "utf8");
    await rename(tempPath, this.filePath);
  }
}

export function createEmptyStore() {
  return {
    version: 1,
    usage: {},
    entitlements: {}
  };
}

function normalizeStore(value) {
  const store = value && typeof value === "object" ? value : createEmptyStore();
  store.version = 1;
  store.usage = store.usage && typeof store.usage === "object" ? store.usage : {};
  store.entitlements = store.entitlements && typeof store.entitlements === "object" ? store.entitlements : {};
  return store;
}

function pruneUsage(store, retentionDays) {
  const maxAgeMs = retentionDays * 24 * 60 * 60 * 1000;
  const cutoff = Date.now() - maxAgeMs;

  for (const [installKey, user] of Object.entries(store.usage)) {
    if (!user || typeof user !== "object" || !user.days) {
      delete store.usage[installKey];
      continue;
    }

    for (const day of Object.keys(user.days)) {
      if (Date.parse(`${day}T00:00:00.000Z`) < cutoff) {
        delete user.days[day];
      }
    }

    if (Object.keys(user.days).length === 0) {
      delete store.usage[installKey];
    }
  }
}
