// Verifies the i18n catalogs (see i18n-catalogs.mjs for the layout). Errors fail the build:
//  - a catalog is missing one of the five languages, or the languages of a catalog have different keys
//  - a catalog defines keys outside its module prefix (core: common./errors./nav./lang./theme.; shared: shared.;
//    layout: layout.; features/<m>: <m>.)
//  - the same key is defined by two modules
//  - a key used in code (i18n.t('…'), '…' | t, fooKey: '…', fooKey="…") is missing in any language
//  - a key is used outside its module (global prefixes are allowed everywhere)
//  - a translation uses other {placeholders} than the German one
//  - errors.<CODE> is missing for a value of ApiErrorCode in server/.../openapi/schemas/common.yaml
// Unused keys are reported as warnings.
import { readFile } from 'node:fs/promises';

import {
  GLOBAL_PREFIXES,
  LANGUAGES,
  loadCatalogs,
  moduleOf,
  ownPrefixes,
  sourceFiles,
} from './i18n-catalogs.mjs';

const COMMON_YAML = '../server/src/main/resources/openapi/schemas/common.yaml';
/** Client-side error codes that are not part of ApiErrorCode. */
const CLIENT_ERROR_CODES = ['NETWORK', 'UNKNOWN', 'CLIENT_ERROR'];
/** Prefixes resolved at runtime (errors.<code>, lang.<lang>, theme.<mode>); never reported as unused. */
const DYNAMIC_PREFIXES = ['errors.', 'lang.', 'theme.'];

const errors = [];
const warnings = [];

// --- catalogs ---------------------------------------------------------------------------------------
const catalogs = await loadCatalogs();
const byDir = new Map();
for (const catalog of catalogs) {
  if (!byDir.has(catalog.dir)) {
    byDir.set(catalog.dir, []);
  }
  byDir.get(catalog.dir).push(catalog);
}

/** key -> module that owns it */
const keyOwner = new Map();
/** lang -> Set of keys */
const keysByLang = Object.fromEntries(LANGUAGES.map((lang) => [lang, new Map()]));

for (const [dir, group] of byDir) {
  const langs = group.map((c) => c.lang);
  for (const lang of LANGUAGES) {
    if (!langs.includes(lang)) {
      errors.push(`${dir}: missing ${lang}.json`);
    }
  }
  const allKeys = new Set(group.flatMap((c) => Object.keys(c.entries)));
  for (const catalog of group) {
    for (const key of allKeys) {
      if (!(key in catalog.entries)) {
        errors.push(`${catalog.path}: missing key "${key}"`);
      }
    }
    const prefixes = ownPrefixes(catalog.module);
    for (const [key, value] of Object.entries(catalog.entries)) {
      if (!prefixes.some((prefix) => key.startsWith(prefix))) {
        errors.push(`${catalog.path}: key "${key}" must start with ${prefixes.join(' or ')}`);
      }
      if (typeof value !== 'string' || value.trim() === '') {
        errors.push(`${catalog.path}: key "${key}" has an empty or non-string value`);
      }
      const existing = keyOwner.get(key);
      if (existing && existing !== catalog.module) {
        errors.push(`Duplicate key "${key}" in ${catalog.module} and ${existing}`);
      }
      keyOwner.set(key, catalog.module);
      if (LANGUAGES.includes(catalog.lang)) {
        keysByLang[catalog.lang].set(key, value);
      }
    }
  }
}

// --- placeholders -----------------------------------------------------------------------------------
function placeholders(text) {
  const names = new Set();
  let depth = 0;
  for (let i = 0; i < text.length; i++) {
    if (text[i] === '{') {
      if (depth === 0) {
        const match = /^\{\s*([a-zA-Z_][\w]*)\s*[,}]/.exec(text.slice(i));
        if (match) {
          names.add(match[1]);
        }
      }
      depth++;
    } else if (text[i] === '}') {
      depth = Math.max(0, depth - 1);
    }
  }
  return [...names].sort().join(',');
}

for (const [key, german] of keysByLang.de) {
  const expected = placeholders(german);
  for (const lang of LANGUAGES) {
    const value = keysByLang[lang].get(key);
    if (value !== undefined && placeholders(value) !== expected) {
      errors.push(
        `${lang}: "${key}" uses placeholders {${placeholders(value)}} but de uses {${expected}}`,
      );
    }
  }
}

// --- usages -----------------------------------------------------------------------------------------
const KEY = '([a-zA-Z][\\w-]*(?:\\.[\\w-]+)+)';
const usagePatterns = [
  new RegExp(`\\bt\\(\\s*['"\`]${KEY}['"\`]`, 'g'), // i18n.t('key') / t('key')
  new RegExp(`['"]${KEY}['"]\\s*\\|\\s*t\\b`, 'g'), // 'key' | t
  new RegExp(`\\w+Key['"]?\\s*[:=]\\s*['"]${KEY}['"]`, 'g'), // labelKey: 'key', titleKey="key"
  new RegExp(`\\btitle\\s*:\\s*['"]${KEY}['"]`, 'g'), // route titles (I18nTitleStrategy)
];
const dynamicPrefixPatterns = [/['"`]([a-zA-Z][\w.]*\.)['"`]\s*\+/g, /`([a-zA-Z][\w.]*\.)\$\{/g];

const files = (await sourceFiles()).filter(
  (file) => !file.endsWith('.spec.ts') && !/[\\/]testing[\\/]/.test(file),
);
const used = new Set();
const dynamicPrefixes = new Set(DYNAMIC_PREFIXES);
let allSource = '';

/** Doc comments contain example keys; only code counts. */
function stripComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

for (const file of files) {
  const source = stripComments(await readFile(file, 'utf8'));
  allSource += `\n${source}`;
  const fileModule = moduleOf(file);
  for (const pattern of dynamicPrefixPatterns) {
    for (const match of source.matchAll(pattern)) {
      dynamicPrefixes.add(match[1]);
    }
  }
  for (const pattern of usagePatterns) {
    for (const match of source.matchAll(pattern)) {
      const key = match[1];
      used.add(key);
      for (const lang of LANGUAGES) {
        if (!keysByLang[lang].has(key)) {
          errors.push(`${file}: key "${key}" is missing in ${lang}`);
        }
      }
      const owner = keyOwner.get(key);
      const global = GLOBAL_PREFIXES.some((prefix) => key.startsWith(prefix));
      if (owner && !global && owner !== fileModule) {
        errors.push(
          `${file}: key "${key}" belongs to ${owner} and may not be used from ${fileModule ?? 'outside a module'}`,
        );
      }
    }
  }
}

// --- ApiErrorCode -----------------------------------------------------------------------------------
async function apiErrorCodes() {
  const yaml = await readFile(COMMON_YAML, 'utf8');
  const lines = yaml.split(/\r?\n/);
  const start = lines.findIndex((line) => /^ApiErrorCode:\s*$/.test(line));
  if (start < 0) {
    throw new Error(`ApiErrorCode not found in ${COMMON_YAML}`);
  }
  const codes = [];
  for (const line of lines.slice(start + 1)) {
    if (/^\S/.test(line) && !line.startsWith('#')) {
      break;
    }
    const match = /^\s+-\s+([A-Z][A-Z0-9_]*)\s*(#.*)?$/.exec(line);
    if (match) {
      codes.push(match[1]);
    }
  }
  return codes;
}

const codes = await apiErrorCodes();
for (const code of [...codes, ...CLIENT_ERROR_CODES]) {
  for (const lang of LANGUAGES) {
    if (!keysByLang[lang].has(`errors.${code}`)) {
      errors.push(`${lang}: missing "errors.${code}" (ApiErrorCode)`);
    }
  }
}

// --- unused -----------------------------------------------------------------------------------------
for (const key of keysByLang.de.keys()) {
  if (used.has(key) || [...dynamicPrefixes].some((prefix) => key.startsWith(prefix))) {
    continue;
  }
  if (
    !allSource.includes(`'${key}'`) &&
    !allSource.includes(`"${key}"`) &&
    !allSource.includes(`\`${key}\``)
  ) {
    warnings.push(`unused key "${key}" (${keyOwner.get(key)})`);
  }
}

for (const warning of warnings) {
  console.warn(`warning: ${warning}`);
}
if (errors.length > 0) {
  console.error([...new Set(errors)].join('\n'));
  console.error(`\ni18n check failed with ${new Set(errors).size} error(s).`);
  process.exit(1);
}
console.log(
  `i18n check passed: ${keysByLang.de.size} keys in ${LANGUAGES.length} languages, ${used.size} static usages, ${codes.length} API error codes.`,
);
