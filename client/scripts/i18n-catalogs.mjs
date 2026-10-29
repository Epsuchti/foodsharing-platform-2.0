// Shared helpers for build-i18n.mjs and check-i18n.mjs.
//
// Catalogs live next to the code that uses them:
//   src/app/core/i18n/<lang>.json            global keys: common.*, errors.*, nav.*, lang.*, theme.*
//   src/app/shared/i18n/<lang>.json          shared.*
//   src/app/layout/i18n/<lang>.json          layout.*
//   src/app/features/<m>/i18n/<lang>.json    <m>.*   (exceptions in FEATURE_PREFIX_OVERRIDES)
// build-i18n.mjs merges them into public/i18n/<lang>.json (gitignored).
import { readdir, readFile } from 'node:fs/promises';
import { join, relative, sep } from 'node:path';

export const LANGUAGES = ['de', 'en', 'gws', 'fr', 'it'];
export const APP_ROOT = 'src/app';

/** Key prefixes owned by core and usable from everywhere. */
export const GLOBAL_PREFIXES = ['common.', 'errors.', 'nav.', 'lang.', 'theme.'];

/**
 * Feature folders whose key prefix differs from the folder name: the folder name would clash with a global prefix
 * (errors, bells) or is kebab-case (keys use camelCase).
 */
export const FEATURE_PREFIX_OVERRIDES = {
  errors: 'errorPages.',
  bells: 'bell.',
  'region-admin': 'regionAdmin.',
  'users-admin': 'usersAdmin.',
  'intro-pickups': 'introPickups.',
  'store-chains': 'storeChains.',
  'store-categories': 'storeCategories.',
  'admin-ops': 'adminOps.',
  'food-share-points': 'foodSharePoints.',
};

/** Returns the module id ("core", "shared", "layout", "features/<m>") of a path below src/app, or null. */
export function moduleOf(path) {
  const parts = relative(APP_ROOT, path).split(sep);
  if (parts[0] === 'features' && parts.length > 1) {
    return `features/${parts[1]}`;
  }
  if (['core', 'shared', 'layout'].includes(parts[0])) {
    return parts[0];
  }
  return null;
}

/** Prefixes a module's own catalog may define. */
export function ownPrefixes(module) {
  if (module === 'core') {
    return GLOBAL_PREFIXES;
  }
  if (module.startsWith('features/')) {
    const name = module.slice('features/'.length);
    return [FEATURE_PREFIX_OVERRIDES[name] ?? `${name}.`];
  }
  return [`${module}.`];
}

async function walk(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(
    entries.map(async (entry) => {
      const path = join(directory, entry.name);
      if (entry.isDirectory()) {
        return entry.name === 'api' && directory === APP_ROOT ? [] : walk(path);
      }
      return [path];
    }),
  );
  return nested.flat();
}

export async function sourceFiles() {
  return (await walk(APP_ROOT)).filter((file) => /\.(ts|html)$/.test(file));
}

/** All catalogs: [{ module, dir, lang, path, entries }] */
export async function loadCatalogs() {
  const files = (await walk(APP_ROOT)).filter((file) => /[\\/]i18n[\\/][a-z]+\.json$/.test(file));
  const catalogs = [];
  for (const path of files) {
    const lang = path.slice(path.lastIndexOf(sep) + 1, -'.json'.length);
    const module = moduleOf(path);
    if (!module) {
      throw new Error(`i18n catalog outside core/, shared/, layout/ or features/<m>/: ${path}`);
    }
    let entries;
    try {
      entries = JSON.parse(await readFile(path, 'utf8'));
    } catch (error) {
      throw new Error(`Invalid JSON in ${path}: ${error.message}`);
    }
    catalogs.push({ module, dir: path.slice(0, path.lastIndexOf(sep)), lang, path, entries });
  }
  return catalogs;
}
