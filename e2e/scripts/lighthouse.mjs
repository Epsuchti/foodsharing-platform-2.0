// Lighthouse check of plan §11.5 (D11.5.3): mobile preset, `/` anonymous and `/onboarding` as newbie@local.
// Thresholds: performance ≥ 90, accessibility ≥ 95.
//
//   E2E_BASE_URL=http://localhost:8080 node scripts/lighthouse.mjs          (CLI, exits 1 below a threshold)
//   import { runLighthouse } from '../scripts/lighthouse.mjs'               (tests/onboarding/d11-lighthouse.spec.ts)
//   E2E_BASE_URL=… node scripts/lighthouse.mjs --report                     (+ report-only pages, docs/lighthouse.md)
//
// Report-only pages (plan-m7 F6: the main M6 pages) are measured and printed but have no thresholds; only the
// default pages decide the exit code.
//
// Chrome: CHROME_PATH, else Playwright's Chromium. The newbie session comes from the dev login endpoint; its pending
// consents are accepted first so `/onboarding` renders the journey and not the consent page.
export const THRESHOLDS = { performance: 90, accessibility: 95 };

async function chromePath() {
  if (process.env.CHROME_PATH) {
    return process.env.CHROME_PATH;
  }
  const { chromium } = await import('@playwright/test');
  return chromium.executablePath();
}

/** Logs in as a seed role through the dev endpoint and returns the Cookie header (pending consents accepted). */
export async function sessionCookie(baseURL, role) {
  const { request } = await import('@playwright/test');
  const context = await request.newContext({ baseURL });
  try {
    const csrf = async () => (await context.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? '';
    await context.get('/api/public/app-info');
    const users = await (await context.get('/api/dev/users')).json();
    const user = users.find((u) => u.email.toLowerCase() === `${role}@local`);
    const login = await context.post('/api/dev/login-as', {
      headers: { 'X-XSRF-TOKEN': await csrf() },
      data: { userId: user?.id },
    });
    if (!login.ok()) {
      throw new Error(`dev login ${role} failed: ${login.status()}`);
    }
    const consents = await (await context.get('/api/legal/consents')).json();
    for (const consent of consents.filter((c) => c.pending)) {
      await context.post('/api/legal/consents', {
        headers: { 'X-XSRF-TOKEN': await csrf() },
        data: { kind: consent.kind, version: consent.currentVersion },
      });
    }
    const { cookies } = await context.storageState();
    return cookies.map((c) => `${c.name}=${c.value}`).join('; ');
  } finally {
    await context.dispose();
  }
}

/**
 * Puts the page's session into Chrome's cookie jar. An `extraHeaders` Cookie header is not enough: as soon as the
 * page itself has cookies (XSRF-TOKEN, fs.lang), Chrome's own Cookie header wins and the session is lost
 * (`/onboarding` then measured the login page with the session-expired dialog).
 */
async function useCookies(port, baseURL, cookie) {
  const { default: puppeteer } = await import('puppeteer-core');
  const browser = await puppeteer.connect({ browserURL: `http://127.0.0.1:${port}` });
  try {
    const session = await browser.target().createCDPSession();
    await session.send('Storage.clearCookies');
    const cookies = (cookie ?? '')
      .split('; ')
      .filter(Boolean)
      .map((pair) => {
        const index = pair.indexOf('=');
        return { name: pair.slice(0, index), value: pair.slice(index + 1), url: baseURL };
      });
    if (cookies.length) {
      await session.send('Storage.setCookies', { cookies });
    }
  } finally {
    await browser.disconnect();
  }
}

/** Runs Lighthouse (mobile) for each page; returns `[{ path, performance, accessibility }]` (scores 0–100). */
export async function runLighthouse(baseURL, pages) {
  const { default: lighthouse } = await import('lighthouse');
  const chromeLauncher = await import('chrome-launcher');
  const chrome = await chromeLauncher.launch({
    chromePath: await chromePath(),
    chromeFlags: ['--headless=new', '--no-sandbox', '--disable-gpu'],
  });
  try {
    const results = [];
    for (const { path, cookie } of pages) {
      await useCookies(chrome.port, baseURL, cookie);
      const run = await lighthouse(
        new URL(path, baseURL).toString(),
        {
          port: chrome.port,
          output: 'json',
          logLevel: 'error',
          onlyCategories: ['performance', 'accessibility'],
          // everything but cookies, which useCookies() just set
          clearStorageTypes: ['file_systems', 'shader_cache', 'service_workers', 'cache_storage', 'indexeddb', 'local_storage', 'websql'],
        },
      );
      const { categories } = run.lhr;
      results.push({
        path,
        performance: Math.round((categories.performance.score ?? 0) * 100),
        accessibility: Math.round((categories.accessibility.score ?? 0) * 100),
      });
    }
    return results;
  } finally {
    await chrome.kill();
  }
}

/**
 * Lighthouse timing is CPU-sensitive: requests every page once (warm-up: JIT, server caches, compressed assets),
 * then runs Lighthouse `runs` times and returns the per-page median of each score.
 */
export async function runLighthouseMedian(baseURL, pages, runs = 3) {
  for (const { path, cookie } of pages) {
    await fetch(new URL(path, baseURL), { headers: cookie ? { cookie } : {} }).then((response) => response.text());
  }
  const all = [];
  for (let i = 0; i < runs; i++) all.push(await runLighthouse(baseURL, pages));
  return pages.map(({ path }) => {
    const scores = all.map((run) => run.find((r) => r.path === path));
    return {
      path,
      performance: median(scores.map((s) => s.performance)),
      accessibility: median(scores.map((s) => s.accessibility)),
      runs: scores.map((s) => s.performance),
    };
  });
}

export function median(values) {
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[mid] : Math.round((sorted[mid - 1] + sorted[mid]) / 2);
}

/** Default pages of D11.5.3. */
export async function defaultPages(baseURL) {
  return [
    { path: '/' },
    { path: '/onboarding', cookie: await sessionCookie(baseURL, 'newbie') },
  ];
}

/** Report-only pages (plan-m7 F6): the main M6 pages, measured for docs/lighthouse.md, no thresholds. */
export async function reportOnlyPages(baseURL) {
  const foodsaver = await sessionCookie(baseURL, 'foodsaver');
  return [
    { path: '/map' },
    { path: '/statistics' },
    { path: '/about/vision' },
    { path: '/blog' },
    { path: '/profile/me', cookie: foodsaver },
    { path: '/baskets', cookie: foodsaver },
    { path: '/food-share-points/00000049-0001-4000-8000-000000000001', cookie: foodsaver },
    { path: '/search?q=Wiedikon', cookie: foodsaver },
    { path: '/regions/00000010-0001-4000-8000-000000000040/reports', cookie: await sessionCookie(baseURL, 'reports') },
  ];
}

export function belowThreshold(results) {
  return results.filter(
    (r) => r.performance < THRESHOLDS.performance || r.accessibility < THRESHOLDS.accessibility,
  );
}

async function main() {
  const baseURL = process.env.E2E_BASE_URL;
  if (!baseURL) {
    console.error('Set E2E_BASE_URL to a running app (e.g. the jar with profile e2e).');
    process.exit(2);
  }
  const runs = Number(process.env.LIGHTHOUSE_RUNS ?? 3);
  const gated = await defaultPages(baseURL);
  const reportOnly = process.argv.includes('--report') ? await reportOnlyPages(baseURL) : [];
  const results = await runLighthouseMedian(baseURL, [...gated, ...reportOnly], runs);
  const gatedResults = results.filter((r) => gated.some((page) => page.path === r.path));
  console.table(results.map((r) => ({ ...r, gate: gatedResults.includes(r) ? 'threshold' : 'report only' })));
  process.exit(belowThreshold(gatedResults).length ? 1 : 0);
}

if ((process.argv[1] ?? '').endsWith('lighthouse.mjs')) {
  void main();
}
