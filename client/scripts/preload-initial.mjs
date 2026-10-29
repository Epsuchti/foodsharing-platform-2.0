// Runs after `ng build`, before precompress.mjs: adds `<link rel="modulepreload">` for every chunk that main.js
// imports statically (transitively). The Angular builder stops after 10 preload links, so the rest of the start-up
// chunks (router, i18n, data-state, …) were only discovered once main.js had been parsed — one more round trip
// before the first page (D11.5.3, Lighthouse LCP on /onboarding).
import { readFile, readdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

const ROOT = 'dist/client/browser';
const STATIC_IMPORT = /(?:^|[;}\s])import\s*(?:[\w$*{][^"']*?from\s*)?["']\.\/([\w.-]+\.js)["']/g;

/** Chunks reached from `entry` through static `import … from "./x.js"` statements (not `import("./x.js")`). */
async function staticGraph(entry) {
  const seen = new Set();
  const visit = async (file) => {
    if (seen.has(file)) return;
    seen.add(file);
    const source = await readFile(join(ROOT, file), 'utf8');
    for (const match of source.matchAll(STATIC_IMPORT)) {
      await visit(match[1]);
    }
  };
  await visit(entry);
  seen.delete(entry);
  return [...seen];
}

const files = await readdir(ROOT);
const main = files.find((file) => /^main-[\w-]+\.js$/.test(file));
if (!main) {
  throw new Error(`no main-*.js in ${ROOT}`);
}
const chunks = await staticGraph(main);
for (const html of files.filter((file) => /^index(\.csr)?\.html$/.test(file))) {
  const path = join(ROOT, html);
  let text = await readFile(path, 'utf8');
  const missing = chunks.filter((chunk) => !text.includes(`href="${chunk}"`));
  if (!missing.length) continue;
  const links = missing.map((chunk) => `<link rel="modulepreload" href="${chunk}">`).join('');
  const anchor = text.lastIndexOf('<link rel="modulepreload"');
  if (anchor < 0) {
    text = text.replace('</head>', `${links}</head>`);
  } else {
    const end = text.indexOf('>', anchor) + 1;
    text = text.slice(0, end) + links + text.slice(end);
  }
  await writeFile(path, text);
  console.log(`preload-initial: ${html} +${missing.length} modulepreload links`);
}
