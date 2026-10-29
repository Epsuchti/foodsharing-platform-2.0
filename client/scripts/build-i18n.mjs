// Merges the per-module catalogs (src/app/**/i18n/<lang>.json) into public/i18n/<lang>.json, plus the start-up part
// public/i18n/<lang>.start.json (START_MODULES only; I18nService loads the rest after the first page, D11.5.3).
// Fails on duplicate keys across modules and on unknown language files.
import { mkdir, writeFile } from 'node:fs/promises';

import { LANGUAGES, loadCatalogs } from './i18n-catalogs.mjs';

/**
 * Modules whose keys the first page needs: the shell (layout, core, shared, the header's bell button, passkey nudge
 * and journey bar), the dev switcher on the login page and the pages that open without the complete catalog (`catalogGuard` in app.routes.ts lists their
 * URLs: `/`, login, onboarding, legal, error pages). Every other route waits for the complete catalog.
 */
export const START_MODULES = [
  'core',
  'shared',
  'layout',
  'features/auth',
  'features/bells',
  'features/onboarding',
  'features/public',
  'features/legal',
  'features/errors',
  // the dev user switcher on the login page (local/e2e only)
  'features/dev',
];

const catalogs = await loadCatalogs();
const errors = [];
const merged = Object.fromEntries(LANGUAGES.map((lang) => [lang, {}]));
const owner = Object.fromEntries(LANGUAGES.map((lang) => [lang, {}]));
const startKeys = new Set();

for (const catalog of catalogs) {
  if (!LANGUAGES.includes(catalog.lang)) {
    errors.push(`Unknown language file ${catalog.path} (allowed: ${LANGUAGES.join(', ')})`);
    continue;
  }
  for (const [key, value] of Object.entries(catalog.entries)) {
    if (typeof value !== 'string') {
      errors.push(`${catalog.path}: value of "${key}" must be a string (catalogs are flat)`);
      continue;
    }
    if (key in merged[catalog.lang]) {
      errors.push(`Duplicate key "${key}" in ${catalog.path} and ${owner[catalog.lang][key]}`);
      continue;
    }
    merged[catalog.lang][key] = value;
    if (START_MODULES.includes(catalog.module)) {
      startKeys.add(key);
    }
    owner[catalog.lang][key] = catalog.path;
  }
}

if (errors.length > 0) {
  console.error(errors.join('\n'));
  process.exit(1);
}

await mkdir('public/i18n', { recursive: true });
for (const lang of LANGUAGES) {
  // Keys missing in a language fall back to German at build time, so the client loads exactly one catalog
  // (D11.5.3). check-i18n.mjs still fails the build on missing translations.
  const sorted = Object.fromEntries(
    Object.entries({ ...merged.de, ...merged[lang] }).sort(([a], [b]) => a.localeCompare(b)),
  );
  await writeFile(`public/i18n/${lang}.json`, `${JSON.stringify(sorted, null, 2)}\n`);
  const start = Object.fromEntries(Object.entries(sorted).filter(([key]) => startKeys.has(key)));
  await writeFile(`public/i18n/${lang}.start.json`, `${JSON.stringify(start)}\n`);
}
console.log(
  `i18n: merged ${catalogs.length} catalogs into public/i18n (${LANGUAGES.map((l) => `${l}: ${Object.keys(merged[l]).length}`).join(', ')}).`,
);
