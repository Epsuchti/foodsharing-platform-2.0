// Load test (D17.1, plan-m7 B7). Run through e2e/load/run.sh (grafana/k6 in Docker against the jar on Postgres).
//
// Scenarios (weighted mix per iteration of the `browse` scenario, 10 → 100 VUs over 5 minutes):
//   landing (app-info, public region, explainer), dashboard (session, journey, me, bells), store list,
//   pickups, messaging (list, messages, send), forum (threads, thread), map (markers, bubble), search, bells.
// The `churn` scenario signs seed users up for a future pickup and out again (one user per VU, no conflicts).
// Thresholds: p95 < 200 ms and failures < 0.1 % overall and per endpoint (tag `name`).
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const BASE = (__ENV.BASE_URL || 'http://host.docker.internal:18180').replace(/\/+$/, '');
const DURATION_SCALE = Number(__ENV.DURATION_SCALE || 1); // 0.1 for a quick dry run
const P95_MS = Number(__ENV.P95_MS || 200);
const OUT_DIR = __ENV.OUT_DIR || '/out';

// Users of the browse mix (seed users that are store members / region members) and of the churn scenario.
const BROWSE_USERS = ['foodsaver@local', 'bieb@local', 'guide@local', 'jumper@local', 'welcome@local', 'orga@local'];
const CHURN_USERS = (__ENV.CHURN_USERS || 'foodsaver@local,bieb@local,guide@local').split(',');

export const ENDPOINTS = [
  'app-info', 'public-region', 'explainer', 'session', 'journey', 'me', 'my-stores', 'region-stores', 'store',
  'pickups', 'my-pickups', 'signup', 'signout', 'conversations', 'messages', 'send-message', 'forum-threads',
  'forum-thread', 'map-markers', 'map-bubble', 'search', 'bells', 'bells-unread', 'activity',
];

const s = (sec) => `${Math.max(1, Math.round(sec * DURATION_SCALE))}s`;
const thresholds = {
  http_req_duration: [`p(95)<${P95_MS}`],
  http_req_failed: ['rate<0.001'],
  checks: ['rate>0.999'],
};
for (const name of ENDPOINTS) {
  thresholds[`http_req_duration{name:${name}}`] = [`p(95)<${P95_MS}`];
  thresholds[`http_req_failed{name:${name}}`] = ['rate<0.001'];
}

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      exec: 'browse',
      startVUs: 10,
      stages: [
        { duration: s(120), target: 100 },
        { duration: s(150), target: 100 },
        { duration: s(30), target: 10 },
      ],
      gracefulRampDown: '10s',
    },
    // one scenario per churn user (k6 VU ids are global, so a shared scenario could pair two VUs with one user)
    ...Object.fromEntries(CHURN_USERS.map((email, i) => [`churn_${i}`, {
      executor: 'constant-vus',
      exec: 'churn',
      vus: 1,
      duration: s(300),
      env: { CHURN_USER: email },
    }])),
  },
  thresholds,
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  setupTimeout: '120s',
  // one login per VU: keep the session cookie across iterations (k6 resets the jar per iteration by default)
  noCookiesReset: true,
  // Only expected status codes count as successful (k6 default: 200-399).
};

// ---------- helpers ----------
function cookie(name) {
  const jar = http.cookieJar();
  const c = jar.cookiesForURL(BASE)[name];
  return c && c.length ? c[0] : undefined;
}

function csrfHeaders() {
  let token = cookie('XSRF-TOKEN');
  if (!token) {
    http.get(`${BASE}/api/public/app-info`, { tags: { name: 'app-info' } });
    token = cookie('XSRF-TOKEN');
  }
  return { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token || '' };
}

let reported = 0; // unexpected statuses logged per VU (diagnostics in the k6 output)
function report(res, name, expected) {
  if (!expected.includes(res.status) && reported < 3) {
    reported += 1;
    console.warn(`${name}: ${res.request.method} ${res.url} -> ${res.status} ${String(res.body).slice(0, 160)}`);
  }
}

function get(path, name, expected = [200]) {
  const res = http.get(`${BASE}${path}`, {
    tags: { name },
    responseCallback: http.expectedStatuses(...expected),
  });
  check(res, { [`${name} status`]: (r) => expected.includes(r.status) });
  report(res, name, expected);
  return res;
}

function send(method, path, name, body, expected = [200]) {
  const res = http.request(method, `${BASE}${path}`, body === undefined ? null : JSON.stringify(body), {
    headers: csrfHeaders(),
    tags: { name },
    responseCallback: http.expectedStatuses(...expected),
  });
  check(res, { [`${name} status`]: (r) => expected.includes(r.status) });
  report(res, name, expected);
  return res;
}

function json(res) {
  try {
    return res.json();
  } catch (_e) {
    return undefined;
  }
}

function items(body) {
  if (!body) return [];
  return Array.isArray(body) ? body : body.items || [];
}

const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];

function login(email, users) {
  const user = users.find((u) => u.email === email);
  if (!user) throw new Error(`seed user ${email} missing`);
  const res = http.post(`${BASE}/api/dev/login-as`, JSON.stringify({ userId: user.id }), {
    headers: csrfHeaders(),
    tags: { name: 'login' },
  });
  if (res.status !== 200) throw new Error(`login-as ${email}: ${res.status}`);
  return user;
}

// setup probes may be refused (403/409); they are not part of the measured traffic
const PROBE = { tags: { name: 'setup-probe' }, responseCallback: http.expectedStatuses({ min: 200, max: 499 }) };

// ---------- setup: discover ids once (seed data), so the script has no hard-coded UUIDs ----------
export function setup() {
  const users = json(http.get(`${BASE}/api/dev/users`));
  if (!users) throw new Error('GET /api/dev/users failed (is the app running with the e2e profile?)');
  const ctx = { users, perUser: {} };
  for (const email of BROWSE_USERS) {
    http.cookieJar().clear(BASE);
    const user = login(email, users);
    // stores whose pickups the user may see (members waiting for approval etc. get 403)
    const stores = items(json(http.get(`${BASE}/api/users/me/stores`))).map((st) => st.id)
      .filter((id) => http.get(`${BASE}/api/stores/${id}/pickups`, PROBE).status === 200);
    // regions only (working groups have no public page and no store list)
    const regions = items(json(http.get(`${BASE}/api/regions/mine`))).filter((r) => r.type !== 'WORKING_GROUP');
    const regionIds = regions.map((r) => r.id);
    const conversations = items(json(http.get(`${BASE}/api/conversations?size=20`))).map((c) => c.id);
    let threads = [];
    for (const rid of regionIds) {
      threads = threads.concat(items(json(http.get(`${BASE}/api/regions/${rid}/forum/threads?size=20`))).map((t) => t.id));
      if (threads.length) break;
    }
    ctx.perUser[email] = {
      id: user.id,
      stores,
      regionIds,
      forumRegion: regionIds.find(Boolean),
      conversations,
      threads,
      regionSlugs: regions.map((r) => r.slug).filter(Boolean),
    };
  }
  // Churn: for each churn user a future pickup with a free slot that the user can sign up for (then sign out again).
  ctx.churn = {};
  for (const email of CHURN_USERS) {
    http.cookieJar().clear(BASE);
    const user = login(email, users);
    const stores = items(json(http.get(`${BASE}/api/users/me/stores`))).map((st) => st.id);
    const taken = new Set(Object.values(ctx.churn).map((c) => `${c.storeId}|${c.pickupAt}`));
    outer: for (const storeId of stores) {
      const pickups = items(json(http.get(`${BASE}/api/stores/${storeId}/pickups`)));
      for (const p of pickups) {
        const key = `${storeId}|${p.pickupAt}`;
        const mine = (p.occupiedSlots || []).some((sl) => sl.user && sl.user.id === user.id);
        if (taken.has(key) || mine || !p.isAvailable || (p.occupiedSlots || []).length >= p.slotCount - 1) continue;
        const at = encodeURIComponent(p.pickupAt);
        const up = http.post(`${BASE}/api/stores/${storeId}/pickups/${at}/signups`, null, { ...PROBE, headers: csrfHeaders() });
        if (up.status !== 200) continue;
        const out = http.del(`${BASE}/api/stores/${storeId}/pickups/${at}/signups/${user.id}`, null, { headers: csrfHeaders() });
        if (out.status !== 200 && out.status !== 204) continue;
        ctx.churn[email] = { userId: user.id, storeId, pickupAt: p.pickupAt };
        break outer;
      }
    }
    if (!ctx.churn[email]) console.warn(`churn: no free pickup for ${email}`);
  }
  http.cookieJar().clear(BASE);
  return ctx;
}

// ---------- browse mix ----------
let me; // per VU

const JOURNEYS = [
  [15, landing],
  [15, dashboard],
  [10, storeList],
  [12, pickups],
  [12, messaging],
  [10, forum],
  [8, map],
  [8, search],
  [10, bells],
];
const TOTAL = JOURNEYS.reduce((a, [w]) => a + w, 0);

export function browse(ctx) {
  if (!me) {
    const email = BROWSE_USERS[(exec.vu.idInTest - 1) % BROWSE_USERS.length];
    login(email, ctx.users);
    me = ctx.perUser[email];
  }
  let r = Math.random() * TOTAL;
  for (const [w, fn] of JOURNEYS) {
    r -= w;
    if (r < 0) {
      fn(me);
      break;
    }
  }
  sleep(0.5 + Math.random());
}

function landing(u) {
  get('/api/public/app-info', 'app-info');
  get(`/api/public/regions/${pick(u.regionSlugs.length ? u.regionSlugs : ['zuerich-stadt'])}`, 'public-region');
  get('/api/public/onboarding/explainer', 'explainer');
}

function dashboard() {
  get('/api/auth/session', 'session');
  get('/api/onboarding/journey', 'journey');
  get('/api/users/me', 'me');
  get('/api/bells/unread-count', 'bells-unread');
  get('/api/activity?size=20', 'activity');
}

function storeList(u) {
  get('/api/users/me/stores', 'my-stores');
  if (u.regionIds.length) get(`/api/regions/${pick(u.regionIds)}/stores`, 'region-stores');
  if (u.stores.length) get(`/api/stores/${pick(u.stores)}`, 'store');
}

function pickups(u) {
  get('/api/users/me/pickups', 'my-pickups');
  if (u.stores.length) get(`/api/stores/${pick(u.stores)}/pickups`, 'pickups');
}

function messaging(u) {
  get('/api/conversations?size=20', 'conversations');
  if (!u.conversations.length) return;
  const id = pick(u.conversations);
  get(`/api/conversations/${id}/messages`, 'messages');
  if (Math.random() < 0.1) send('POST', `/api/conversations/${id}/messages`, 'send-message', { body: 'Lasttest' }, [200, 201]);
}

function forum(u) {
  if (u.forumRegion) get(`/api/regions/${u.forumRegion}/forum/threads?size=20`, 'forum-threads');
  if (u.threads.length) get(`/api/forum/threads/${pick(u.threads)}`, 'forum-thread');
}

function map(u) {
  const res = get('/api/map/markers/stores', 'map-markers');
  const markers = items(json(res));
  const m = markers.length ? pick(markers) : undefined;
  if (m && m.id) get(`/api/map/bubbles/store/${m.id}`, 'map-bubble');
  else if (u.stores.length) get(`/api/map/bubbles/store/${pick(u.stores)}`, 'map-bubble');
}

function search() {
  get(`/api/search?q=${pick(['brot', 'zuerich', 'migros', 'coop', 'gemuese', 'baeckerei'])}`, 'search');
}

function bells() {
  get('/api/bells?size=20', 'bells');
  get('/api/bells/unread-count', 'bells-unread');
}

// ---------- churn ----------
let churnUser;
export function churn(ctx) {
  const email = __ENV.CHURN_USER;
  const c = ctx.churn[email];
  if (!c) {
    sleep(5);
    return;
  }
  if (!churnUser) {
    login(email, ctx.users);
    churnUser = c;
  }
  const at = encodeURIComponent(c.pickupAt);
  send('POST', `/api/stores/${c.storeId}/pickups/${at}/signups`, 'signup', undefined);
  sleep(0.5);
  send('DELETE', `/api/stores/${c.storeId}/pickups/${at}/signups/${c.userId}`, 'signout', undefined, [200, 204]);
  sleep(0.5);
}

// ---------- summary: JSON + markdown table (docs/load-test.md is assembled by run.sh) ----------
function metric(data, key) {
  return data.metrics[key] ? data.metrics[key].values : undefined;
}

function row(data, label, durKey, failKey) {
  const d = metric(data, durKey);
  const f = metric(data, failKey);
  if (!d || !d.count && d['p(95)'] === undefined) return undefined;
  const reqs = f ? f.passes + f.fails : 0;
  const failRate = f ? f.rate : 0;
  const ok = d['p(95)'] < P95_MS && failRate < 0.001;
  return `| ${label} | ${reqs} | ${d.med.toFixed(1)} | ${d['p(95)'].toFixed(1)} | ${d['p(99)'].toFixed(1)} | ${d.max.toFixed(1)} | ${(failRate * 100).toFixed(2)} % | ${ok ? 'pass' : '**FAIL**'} |`;
}

export function handleSummary(data) {
  const lines = [
    `| Endpoint (tag \`name\`) | Requests | Median ms | p95 ms | p99 ms | Max ms | Failed | p95 < ${P95_MS} ms, failed < 0.1 % |`,
    '|---|---:|---:|---:|---:|---:|---:|---|',
  ];
  const all = row(data, '**all**', 'http_req_duration', 'http_req_failed');
  if (all) lines.push(all);
  for (const name of ENDPOINTS) {
    const r = row(data, name, `http_req_duration{name:${name}}`, `http_req_failed{name:${name}}`);
    if (r) lines.push(r);
  }
  const reqs = metric(data, 'http_reqs');
  const vus = metric(data, 'vus_max');
  const iters = metric(data, 'iterations');
  const meta = [
    `- Requests: ${reqs ? reqs.count : 0} (${reqs ? reqs.rate.toFixed(1) : 0} req/s), iterations: ${iters ? iters.count : 0}, max VUs: ${vus ? vus.max : 0}`,
    `- Checks passed: ${metric(data, 'checks') ? (metric(data, 'checks').rate * 100).toFixed(2) : '-'} %`,
  ];
  return {
    [`${OUT_DIR}/summary.json`]: JSON.stringify(data, null, 2),
    [`${OUT_DIR}/table.md`]: `${lines.join('\n')}\n\n${meta.join('\n')}\n`,
    stdout: `${lines.join('\n')}\n${meta.join('\n')}\n`,
  };
}
