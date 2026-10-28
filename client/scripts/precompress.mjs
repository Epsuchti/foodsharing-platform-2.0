// Runs after `ng build`: writes Brotli (.br) and gzip (.gz) siblings of the text files in dist/client/browser.
// Spring serves them via EncodedResourceResolver (StaticResourceConfig); Brotli at quality 11 is ~15–20 % smaller
// than the on-the-fly gzip, which matters for the cold mobile load (D11.5.3, Lighthouse).
import { readdir, readFile, stat, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { brotliCompressSync, constants, gzipSync } from 'node:zlib';

const ROOT = 'dist/client/browser';
const TEXT = /\.(js|mjs|css|json|html|svg|webmanifest|txt)$/;
const MIN_BYTES = 1024;

async function* files(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      yield* files(path);
    } else if (TEXT.test(entry.name)) {
      yield path;
    }
  }
}

let count = 0;
let before = 0;
let after = 0;
for await (const path of files(ROOT)) {
  if ((await stat(path)).size < MIN_BYTES) {
    continue;
  }
  const content = await readFile(path);
  const br = brotliCompressSync(content, {
    params: {
      [constants.BROTLI_PARAM_QUALITY]: 11,
      [constants.BROTLI_PARAM_SIZE_HINT]: content.length,
    },
  });
  await writeFile(`${path}.br`, br);
  await writeFile(`${path}.gz`, gzipSync(content, { level: 9 }));
  count++;
  before += content.length;
  after += br.length;
}
console.log(
  `precompress: ${count} files, ${Math.round(before / 1024)} kB → ${Math.round(after / 1024)} kB (br)`,
);
