import { Firestore, FieldValue } from "@google-cloud/firestore";
import { currentDayKey } from "./usage.js";

export class FirestoreStore {
  constructor(config) {
    this.firestore = new Firestore();
    this.usage = this.firestore.collection(config.firestoreUsageCollection);
    this.entitlements = this.firestore.collection(config.firestoreEntitlementsCollection);
  }

  async getUsageStatus(installKey, kind, limit, now = new Date()) {
    const day = currentDayKey(now);
    const doc = await this.dayRef(installKey, day).get();
    const data = doc.exists ? doc.data() : {};
    const used = Number(data?.[kind] || 0);
    return {
      allowed: used < limit,
      remaining: Math.max(0, limit - used),
      limit,
      used,
      kind,
      day
    };
  }

  async consumeUsage(installKey, kind, limit, now = new Date()) {
    const day = currentDayKey(now);
    const ref = this.dayRef(installKey, day);

    return this.firestore.runTransaction(async (transaction) => {
      const doc = await transaction.get(ref);
      const data = doc.exists ? doc.data() : {};
      const used = Number(data?.[kind] || 0);

      if (used >= limit) {
        transaction.set(ref, { lastSeenAt: now.toISOString() }, { merge: true });
        return {
          allowed: false,
          remaining: 0,
          limit,
          used,
          kind,
          day
        };
      }

      const next = used + 1;
      transaction.set(ref, {
        [kind]: next,
        firstSeenAt: data?.firstSeenAt || now.toISOString(),
        lastSeenAt: now.toISOString()
      }, { merge: true });

      return {
        allowed: true,
        remaining: Math.max(0, limit - next),
        limit,
        used: next,
        kind,
        day
      };
    });
  }

  async recordEntitlement(tokenHash, entitlement) {
    await this.entitlements.doc(tokenHash).set({
      ...entitlement,
      updatedAt: FieldValue.serverTimestamp()
    }, { merge: true });
  }

  dayRef(installKey, day) {
    return this.usage.doc(installKey).collection("days").doc(day);
  }
}

export function createStore(config) {
  if (config.storageDriver === "firestore") {
    return new FirestoreStore(config);
  }
  return null;
}
