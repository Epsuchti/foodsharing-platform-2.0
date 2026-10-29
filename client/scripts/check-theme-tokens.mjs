// Fails when a component is imported from `@openng/optimus-ui/<name>` (src/**) but its Aura design tokens are not
// listed in src/app/core/theme/aura-lean.ts — the app ships only the tokens of the components it uses (D11.5.3).
import { existsSync } from 'node:fs';
import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';

const LEAN = 'src/app/core/theme/aura-lean.ts';
const TOKENS = 'node_modules/@openng/optimus-ui-themes/dist/aura';

async function* sources(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== 'api') yield* sources(path);
    } else if (entry.name.endsWith('.ts')) {
      yield path;
    }
  }
}

const lean = await readFile(LEAN, 'utf8');
const listed = new Set([...lean.matchAll(/@openng\/optimus-ui-themes\/aura\/([a-z]+)'/g)].map((m) => m[1]));
const missing = new Map();
for await (const file of sources('src')) {
  const text = await readFile(file, 'utf8');
  for (const [, name] of text.matchAll(/@openng\/optimus-ui\/([a-z]+)'/g)) {
    if (existsSync(join(TOKENS, name)) && !listed.has(name)) {
      missing.set(name, file);
    }
  }
}
if (missing.size) {
  for (const [name, file] of missing) {
    console.error(`theme tokens: '${name}' (imported in ${file}) is missing in ${LEAN}`);
  }
  process.exit(1);
}
console.log(`Theme token check passed (${listed.size} component token sets).`);
