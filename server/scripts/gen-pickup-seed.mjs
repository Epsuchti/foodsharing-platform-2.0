#!/usr/bin/env node
// Generates the pickup seed CSVs (B4, docs/seed-ids.md module 0021) relative to SEED_NOW = 2026-11-02T09:00Z
// (a Monday). Deterministic: running it again produces identical files. Usage:
//   node server/scripts/gen-pickup-seed.mjs
// Writes server/src/main/resources/db/seed/pickup/{regular_pickup,pickup,pickup_signup}.csv.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const OUT = join(dirname(fileURLToPath(import.meta.url)), '../src/main/resources/db/seed/pickup');
const SEED_NOW = Date.parse('2026-11-02T09:00:00Z');
const DAY = 86400000;
const BASE = '2026-01-15T10:00:00Z';
const DST_END = Date.parse('2026-10-25T01:00:00Z'); // Europe/Zurich CEST -> CET (only transition in range)

const store = (n) => `00000020-0001-4000-8000-${String(n).padStart(12, '0')}`;
const user = (n) => `00000001-0001-4000-8000-${String(n).padStart(12, '0')}`;
const id = (kind, n) => `00000021-${String(kind).padStart(4, '0')}-4000-8000-${String(n).padStart(12, '0')}`;
const iso = (ms) => new Date(ms).toISOString().replace('.000Z', 'Z');

const FOODSAVER = 6, JUMPER = 7, SLEEPY = 8, BIEB = 9, GUIDE = 10, BOT = 11, WELCOME = 12;
const DAYS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

// store -> weekly schedule [weekday, HH:mm, slots]; team = regular members (managers first), prefetch days
const STORES = {
  1: { team: [BIEB, FOODSAVER, SLEEPY], prefetch: 14, schedule: [['MONDAY', '19:00', 3], ['THURSDAY', '19:00', 3]] },
  2: { team: [BIEB, FOODSAVER, JUMPER], prefetch: 14, schedule: [['WEDNESDAY', '18:00', 2], ['SATURDAY', '18:00', 2]] },
  3: { team: [BOT, FOODSAVER, GUIDE], prefetch: 7, schedule: [['TUESDAY', '18:30', 3], ['FRIDAY', '18:30', 3]] },
  4: { team: [WELCOME, FOODSAVER, BOT], prefetch: 14, schedule: [['WEDNESDAY', '18:30', 2], ['SATURDAY', '19:30', 2]] },
  5: { team: [BIEB, WELCOME], prefetch: 14, schedule: [['MONDAY', '20:00', 3], ['THURSDAY', '20:00', 2]] },
  6: { team: [BIEB, SLEEPY], prefetch: 14, schedule: [['TUESDAY', '19:00', 2]] },
  7: { team: [WELCOME, FOODSAVER], prefetch: 14, schedule: [['WEDNESDAY', '20:30', 2], ['FRIDAY', '19:00', 2]] },
  8: { team: [BIEB], prefetch: 14, schedule: [['THURSDAY', '18:00', 4]] },
  9: { team: [BIEB, GUIDE], prefetch: 14, schedule: [['MONDAY', '18:30', 2], ['FRIDAY', '20:00', 2]] },
  10: { team: [BOT, WELCOME], prefetch: 14, schedule: [['TUESDAY', '20:00', 3]] },
  11: { team: [BIEB, BOT, WELCOME], prefetch: 14, schedule: [['WEDNESDAY', '19:00', 2], ['SATURDAY', '18:30', 3]] },
  12: { team: [BIEB], prefetch: 14, schedule: [['FRIDAY', '18:00', 2]] },
  13: { team: [BOT], prefetch: 14, schedule: [['MONDAY', '19:30', 2], ['THURSDAY', '18:30', 2]] },
  14: { team: [BOT], prefetch: 14, schedule: [['SATURDAY', '20:00', 3]] },
  15: { team: [BIEB], prefetch: 14, schedule: [['TUESDAY', '18:00', 2], ['THURSDAY', '20:30', 2]] },
  16: { team: [WELCOME], prefetch: 14, schedule: [['WEDNESDAY', '19:30', 4]] },
  23: { team: [BOT], prefetch: 14, schedule: [['MONDAY', '18:00', 2], ['FRIDAY', '19:30', 2]] },
  25: { team: [BOT], prefetch: 42, schedule: [['SATURDAY', '18:00', 3]] },
};
const MANAGERS = { 1: BIEB, 2: BIEB, 3: BOT, 4: WELCOME, 5: BIEB, 6: BIEB, 7: WELCOME, 8: BIEB, 9: BIEB, 10: BOT,
  11: BIEB, 12: BIEB, 13: BOT, 14: BOT, 15: BIEB, 16: WELCOME, 23: BOT, 25: BOT };

/** Local Zürich date (UTC midnight ms of that calendar day) + HH:mm -> UTC instant. */
function zurich(dayMs, hhmm) {
  const [h, m] = hhmm.split(':').map(Number);
  const guess = dayMs + (h * 60 + m) * 60000;
  return guess - (guess - 2 * 3600000 < DST_END ? 2 : 1) * 3600000;
}

/** Occurrences of a store's schedule with from <= at < to. */
function occurrences(s, from, to) {
  const out = [];
  for (let day = Math.floor(from / DAY) * DAY - DAY; day < to + DAY; day += DAY) {
    const weekday = DAYS[new Date(day).getUTCDay()];
    for (const [wd, time, slots] of STORES[s].schedule) {
      if (wd !== weekday) continue;
      const at = zurich(day, time);
      if (at >= from && at < to) out.push({ store: s, at, slots, time });
    }
  }
  return out.sort((a, b) => a.at - b.at);
}

const signups = []; // {store, user, at, confirmed}
const key = (s, at) => `${s}@${at}`;
const taken = new Map();
function add(s, u, at, confirmed = true) {
  const k = key(s, at);
  const list = taken.get(k) ?? [];
  if (list.includes(u)) throw new Error(`duplicate ${u} ${k}`);
  list.push(u);
  taken.set(k, list);
  signups.push({ store: s, user: u, at, confirmed });
}
const free = (o) => (taken.get(key(o.store, o.at)) ?? []).length < o.slots;

// ---------------------------------------------------------------- past 90 days (all confirmed)
const pastFrom = SEED_NOW - 90 * DAY;
const past = Object.keys(STORES).flatMap((s) => occurrences(Number(s), pastFrom, SEED_NOW));

// foodsaver: the two pickups of the last 7 days (S-01 Thursday 2026-10-29, S-04 Wednesday 2026-10-28)
const fsRecent = past.filter((o) => o.at >= SEED_NOW - 7 * DAY && ((o.store === 1 && o.time === '19:00'
  && new Date(o.at).getUTCDay() === 4) || (o.store === 4 && new Date(o.at).getUTCDay() === 3)));
if (fsRecent.length !== 2) throw new Error('foodsaver recent pickups');
fsRecent.forEach((o) => add(o.store, FOODSAVER, o.at));

const QUOTAS = [
  [FOODSAVER, 22, (o) => o.at < SEED_NOW - 7 * DAY],
  [JUMPER, 6, () => true],
  [SLEEPY, 3, (o) => o.at < SEED_NOW - 60 * DAY],
  [BIEB, 30, () => true],
  [GUIDE, 12, () => true],
  [BOT, 10, () => true],
  [WELCOME, 8, () => true],
];
for (const [u, n, filter] of QUOTAS) {
  const candidates = past.filter((o) => STORES[o.store].team.includes(u) && filter(o));
  const stride = Math.max(1, Math.floor(candidates.length / n));
  const order = [];
  for (let off = 0; off < stride; off++) for (let i = off; i < candidates.length; i += stride) order.push(candidates[i]);
  let picked = 0;
  for (const o of order) {
    if (picked === n) break;
    if (free(o) && !(taken.get(key(o.store, o.at)) ?? []).includes(u)) {
      add(o.store, u, o.at);
      picked++;
    }
  }
  if (picked < n) throw new Error(`not enough pickups for user ${u}`);
}

// ---------------------------------------------------------------- next 30 days
const dates = []; // one-time rows {store, at, slots, description}
const at = (s, dayOffset, time) => zurich(Math.floor(SEED_NOW / DAY) * DAY + dayOffset * DAY, time);

const s01Thursday = at(1, 3, '19:00');
const s04Wednesday = at(4, 2, '18:30');
const s02Saturday = at(2, 5, '18:00');
const s01Sunday = at(1, 6, '10:00');
const s05Monday = at(5, 7, '20:00');
const special = new Set([key(1, s01Thursday), key(4, s04Wednesday), key(2, s02Saturday), key(5, s05Monday)]);

add(1, FOODSAVER, s01Thursday, false);
add(1, SLEEPY, s01Thursday, true);
add(4, FOODSAVER, s04Wednesday, true);
add(2, BIEB, s02Saturday, true);
add(2, JUMPER, s02Saturday, true);
dates.push({ store: 1, at: s01Sunday, slots: 2, description: 'Sonntags-Extra: Marktreste vom Samstag' });
dates.push({ store: 5, at: s05Monday, slots: 0, description: null });

for (const s of Object.keys(STORES).map(Number)) {
  const horizon = SEED_NOW + Math.min(STORES[s].prefetch, 30) * DAY;
  const others = STORES[s].team.filter((u) => u !== FOODSAVER);
  occurrences(s, SEED_NOW, horizon).forEach((o, k) => {
    if (special.has(key(s, o.at))) return;
    const count = Math.min(k % 3, o.slots - 1, others.length);
    for (let j = 0; j < count; j++) {
      const u = others[(k + j) % others.length];
      add(s, u, o.at, u === MANAGERS[s] || (k + j) % 4 !== 3);
    }
  });
}
// S-03: next Tuesday's pickup has 5 slots
const s03Next = occurrences(3, SEED_NOW, SEED_NOW + 7 * DAY)[0];
dates.push({ store: 3, at: s03Next.at, slots: 5, description: null });

// every future sign-up has its pickup row (A20: signing up converts the occurrence)
const regularOf = (s, t) => occurrences(s, t, t + 1)[0];
for (const sg of signups.filter((x) => x.at > SEED_NOW)) {
  if (dates.some((d) => d.store === sg.store && d.at === sg.at)) continue;
  const o = regularOf(sg.store, sg.at);
  if (!o) throw new Error(`no occurrence ${sg.store} ${iso(sg.at)}`);
  dates.push({ store: sg.store, at: sg.at, slots: o.slots, description: null });
}

// ---------------------------------------------------------------- CSVs
const head = 'id,version,created_at,updated_at,created_by,updated_by';
const csv = (s) => (s === null || s === undefined ? 'NULL' : /[",]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s);
mkdirSync(OUT, { recursive: true });

let n = 0;
const regularRows = [`${head},store_id,weekday,start_time,slot_count,description`];
for (const s of Object.keys(STORES).map(Number)) {
  for (const [wd, time, slots] of STORES[s].schedule) {
    regularRows.push(`${id(1, ++n)},0,${BASE},${BASE},NULL,NULL,${store(s)},${wd},${time}:00,${slots},NULL`);
  }
}
writeFileSync(join(OUT, 'regular_pickup.csv'), `${regularRows.join('\n')}\n`);

dates.sort((a, b) => a.at - b.at || a.store - b.store);
const dateRows = [`${head},store_id,pickup_at,slot_count,description`];
dates.forEach((d, i) => {
  const created = iso(Math.min(d.at, SEED_NOW) - 7 * DAY);
  dateRows.push(`${id(2, i + 1)},0,${created},${created},NULL,NULL,${store(d.store)},${iso(d.at)},${d.slots},`
    + `${csv(d.description)}`);
});
writeFileSync(join(OUT, 'pickup.csv'), `${dateRows.join('\n')}\n`);

signups.sort((a, b) => a.at - b.at || a.store - b.store || a.user - b.user);
const signupRows = [`${head},store_id,user_id,pickup_at,confirmed,confirmed_by,confirmed_at,reminded_at`];
signups.forEach((sg, i) => {
  const created = iso(Math.min(sg.at - 3 * DAY, SEED_NOW - DAY));
  const by = sg.confirmed ? user(MANAGERS[sg.store]) : 'NULL';
  signupRows.push(`${id(3, i + 1)},0,${created},${created},${user(sg.user)},${user(sg.user)},${store(sg.store)},`
    + `${user(sg.user)},${iso(sg.at)},${sg.confirmed},${by},${sg.confirmed ? created : 'NULL'},NULL`);
});
writeFileSync(join(OUT, 'pickup_signup.csv'), `${signupRows.join('\n')}\n`);

const perUser = {};
signups.filter((x) => x.at < SEED_NOW).forEach((x) => { perUser[x.user] = (perUser[x.user] ?? 0) + 1; });
console.log(`regular ${regularRows.length - 1}, pickup ${dateRows.length - 1}, signups ${signups.length} `
  + `(past ${signups.filter((x) => x.at < SEED_NOW).length}), past per user ${JSON.stringify(perUser)}`);
