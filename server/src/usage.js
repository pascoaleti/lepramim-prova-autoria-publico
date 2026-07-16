export const USAGE_KINDS = new Set(["screen", "image"]);

export function normalizeUsageKind(kind) {
  return String(kind || "").trim().toLowerCase();
}

export function isValidInstallKey(value) {
  return typeof value === "string" && /^[a-f0-9]{64}$/i.test(value);
}

export function currentDayKey(now = new Date()) {
  return now.toISOString().slice(0, 10);
}

export function getUsageStatus(store, installKey, kind, limit, now = new Date()) {
  const day = currentDayKey(now);
  const usage = readDayUsage(store, installKey, day);
  const used = Number(usage[kind] || 0);

  return {
    allowed: used < limit,
    remaining: Math.max(0, limit - used),
    limit,
    used,
    kind,
    day
  };
}

export function consumeUsage(store, installKey, kind, limit, now = new Date()) {
  const day = currentDayKey(now);
  const user = ensureUser(store, installKey);
  const usage = user.days[day] || { screen: 0, image: 0 };
  const used = Number(usage[kind] || 0);

  user.lastSeenAt = now.toISOString();
  if (used >= limit) {
    user.days[day] = usage;
    return {
      allowed: false,
      remaining: 0,
      limit,
      used,
      kind,
      day
    };
  }

  usage[kind] = used + 1;
  user.days[day] = usage;

  return {
    allowed: true,
    remaining: Math.max(0, limit - usage[kind]),
    limit,
    used: usage[kind],
    kind,
    day
  };
}

function readDayUsage(store, installKey, day) {
  return store.usage?.[installKey]?.days?.[day] || { screen: 0, image: 0 };
}

function ensureUser(store, installKey) {
  store.usage[installKey] = store.usage[installKey] || { firstSeenAt: new Date().toISOString(), days: {} };
  store.usage[installKey].days = store.usage[installKey].days || {};
  return store.usage[installKey];
}
