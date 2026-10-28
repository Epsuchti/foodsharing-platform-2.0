// Merges the per-module catalogs (src/app/**/i18n/<lang>.json) into public/i18n/<lang>.json.
// Fails on duplicate keys across modules and on unknown language files.
import { mkdir, writeFile } from 'node:fs/promises';

import { LANGUAGES, loadCatalogs } from './i18n-catalogs.mjs';

const catalogs = await loadCatalogs();
const errors = [];
const merged = Object.fromEntries(LANGUAGES.map((lang) => [lang, {}]));
const owner = Object.fromEntries(LANGUAGES.map((lang) => [lang, {}]));

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
}
console.log(
  `i18n: merged ${catalogs.length} catalogs into public/i18n (${LANGUAGES.map((l) => `${l}: ${Object.keys(merged[l]).length}`).join(', ')}).`,
);
