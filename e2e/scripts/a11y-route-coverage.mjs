#!/usr/bin/env node
/**
 * Route coverage of the axe sweep (plan-m7 F6): fails when a route of the Angular app has no entry in
 * `tests/a11y/routes.ts` (the table `tests/a11y/pages.spec.ts` sweeps) and is not in that file's exclusion list.
 *
 * The route tree is read at runtime, not parsed: `client/src/app/app.routes.ts` is bundled with esbuild (from
 * client/node_modules) for Node, every lazy *component* import is replaced by a stub, every lazy `*.routes` import
 * stays real, and the tree is walked like the router does (children + resolved `loadChildren`), so helper
 * functions (`tab()`, `section()`), spreads over `LANGUAGES`/`ABOUT_PAGES` and new route files are all seen.
 *
 * A sweep path (`/regions/<uuid>/members`) covers the first route pattern it matches in router order. Patterns that
 * only redirect (`redirectTo`, or a guard-only route without component) need an explicit exclusion like `/dev/*`.
 *
 * Needs the generated client API (`npm run generate` in client/, done by the Maven build).
 * Usage: node scripts/a11y-route-coverage.mjs [--list]
 */
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import fs from 'node:fs';
import os from 'node:os';

const here = path.dirname(fileURLToPath(import.meta.url));
const e2eDir = path.resolve(here, '..');
const clientDir = path.resolve(e2eDir, '..', 'client');
const require = createRequire(path.join(clientDir, 'package.json'));
const esbuild = require('esbuild');

const STUB = 'a11y-coverage-stub';

/** Lazy component imports become a stub; lazy route files stay real. */
const stubComponents = {
  name: 'stub-lazy-components',
  setup(build) {
    build.onResolve({ filter: /.*/ }, (args) => {
      if (args.kind === 'dynamic-import' && !/\.routes$/.test(args.path)) return { path: args.path, namespace: STUB };
      return undefined;
    });
    build.onLoad({ filter: /.*/, namespace: STUB }, () => ({
      contents: 'export default new Proxy({}, { get: (_, key) => (key === "then" ? undefined : class LazyStub {}) });',
      loader: 'js',
    }));
  },
};

async function bundle(entry, { angular = false } = {}) {
  const result = await esbuild.build({
    // Partially compiled Angular packages need the JIT compiler facade when they are evaluated outside a build.
    stdin: {
      contents: `${angular ? "import '@angular/compiler';\n" : ''}export * from ${JSON.stringify(entry.replace(/\\/g, '/'))};`,
      resolveDir: path.dirname(entry),
      loader: 'ts',
    },
    bundle: true,
    write: false,
    platform: 'node',
    format: 'esm',
    target: 'node22',
    tsconfig: path.join(clientDir, 'tsconfig.json'),
    plugins: [stubComponents],
    nodePaths: [path.join(clientDir, 'node_modules')],
    logLevel: 'silent',
    // Some Angular packages read `ngDevMode`/`ngJitMode` at import time.
    banner: { js: 'globalThis.ngDevMode = false; globalThis.ngJitMode = true;' },
  });
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'a11y-cov-')), 'bundle.mjs');
  fs.writeFileSync(file, result.outputFiles[0].text);
  return import(pathToFileURL(file).href);
}

/** Full route patterns in router order: `{ pattern, redirect }` for every route that renders or redirects. */
async function collect(routes, prefix = []) {
  const out = [];
  for (const route of routes) {
    const segments = [...prefix, ...(route.path ? route.path.split('/') : [])];
    const pattern = '/' + segments.join('/');
    let children = route.children;
    if (route.loadChildren) {
      const loaded = await route.loadChildren();
      children = Array.isArray(loaded) ? loaded : (loaded?.default ?? loaded);
    }
    const renders = !!(route.component || route.loadComponent);
    if (route.redirectTo !== undefined) out.push({ pattern, redirect: true });
    else if (children?.length) {
      // A component with an empty child list is a page of its own (children only add tabs).
      out.push(...(await collect(children, segments)));
    } else out.push({ pattern, redirect: !renders });
  }
  return out;
}

function matches(pattern, url) {
  const p = pattern.split('/').filter(Boolean);
  const u = url.split(/[?#]/)[0].split('/').filter(Boolean);
  if (p[0] === '**') return true;
  if (p.length !== u.length) return false;
  return p.every((segment, i) => segment.startsWith(':') || segment === u[i]);
}

const wildcard = (glob) => new RegExp('^' + glob.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*') + '$');

const appRoutes = await bundle(path.join(clientDir, 'src', 'app', 'app.routes.ts'), { angular: true });
const patterns = await collect(appRoutes.routes);
const sweep = await bundle(path.join(e2eDir, 'tests', 'a11y', 'routes.ts'));

const covered = new Map();
const unmatched = [];
for (const entry of sweep.SWEEP_ROUTES) {
  const hit = patterns.find((route) => matches(route.pattern, entry.path));
  if (!hit) unmatched.push(entry.path);
  else covered.set(hit.pattern, [...(covered.get(hit.pattern) ?? []), entry.path]);
}
const excluded = (pattern) => sweep.SWEEP_EXCLUDED.some((rule) => wildcard(rule.pattern).test(pattern));
const missing = patterns.filter((route) => !covered.has(route.pattern) && !excluded(route.pattern) && route.pattern !== '/**');
const staleExclusions = sweep.SWEEP_EXCLUDED.filter((rule) => !patterns.some((route) => wildcard(rule.pattern).test(route.pattern)));
const coveredButExcluded = [...covered.keys()].filter(excluded);

if (process.argv.includes('--list')) {
  for (const route of patterns) {
    const mark = covered.has(route.pattern) ? 'swept   ' : excluded(route.pattern) ? 'excluded' : 'MISSING ';
    console.log(`${mark} ${route.pattern}${route.redirect ? '  (redirect)' : ''}`);
  }
}
console.log(`${patterns.length} route patterns, ${covered.size} swept, ${patterns.filter((r) => excluded(r.pattern)).length} excluded`);

let failed = false;
if (missing.length) {
  failed = true;
  console.error(`\nRoutes without an axe sweep entry (add them to e2e/tests/a11y/routes.ts or, with a reason, to SWEEP_EXCLUDED):`);
  for (const route of missing) console.error(`  ${route.pattern}${route.redirect ? '  (redirect)' : ''}`);
}
if (unmatched.length) {
  failed = true;
  console.error(`\nSweep entries that match no route (only the not-found page may be swept on purpose, as '/does-not-exist'):`);
  for (const url of unmatched) console.error(`  ${url}`);
}
if (staleExclusions.length) {
  failed = true;
  console.error(`\nExclusions that match no route any more:`);
  for (const rule of staleExclusions) console.error(`  ${rule.pattern}`);
}
if (coveredButExcluded.length) {
  failed = true;
  console.error(`\nExcluded routes that are swept anyway (drop the exclusion):`);
  for (const pattern of coveredButExcluded) console.error(`  ${pattern}`);
}
process.exit(failed ? 1 : 0);
