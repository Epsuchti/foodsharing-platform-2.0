// Enforces the DataState rule (docs/conventions.md A7): files below src/app/features/** may call methods of a
// generated OpenAPI service (src/app/api, class name *Service) only inside loadResource(...) or runMutation(...)
// arguments. Files named *.resource.ts are exempt (they wrap loaders for reuse). Spec files are ignored.
import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';

const ROOT = 'src/app/features';

async function walk(directory) {
  let entries;
  try {
    entries = await readdir(directory, { withFileTypes: true });
  } catch {
    return [];
  }
  const nested = await Promise.all(
    entries.map((entry) =>
      entry.isDirectory() ? walk(join(directory, entry.name)) : [join(directory, entry.name)],
    ),
  );
  return nested.flat();
}

/** Ranges [start, end) of the argument lists of every `name(` call. */
function callRanges(source, names) {
  const ranges = [];
  const pattern = new RegExp(`\\b(${names.join('|')})\\s*(<[^>]*>)?\\s*\\(`, 'g');
  for (const match of source.matchAll(pattern)) {
    const open = match.index + match[0].length - 1;
    let depth = 0;
    for (let i = open; i < source.length; i++) {
      if (source[i] === '(') depth++;
      else if (source[i] === ')') {
        depth--;
        if (depth === 0) {
          ranges.push([open, i]);
          break;
        }
      }
    }
  }
  return ranges;
}

function lineOf(source, index) {
  return source.slice(0, index).split('\n').length;
}

const violations = [];
const files = (await walk(ROOT)).filter(
  (file) => file.endsWith('.ts') && !file.endsWith('.spec.ts') && !file.endsWith('.resource.ts'),
);

for (const file of files) {
  const source = await readFile(file, 'utf8');
  // Generated service classes imported (relatively) from src/app/api.
  const services = new Set();
  for (const match of source.matchAll(
    /import\s*\{([^}]*)\}\s*from\s*['"]\.{1,2}\/(?:\.\.\/)*api(?:\/[^'"]*)?['"]/g,
  )) {
    for (const name of match[1].split(',').map((part) =>
      part
        .trim()
        .split(/\s+as\s+/)
        .pop(),
    )) {
      if (/Service$/.test(name)) {
        services.add(name);
      }
    }
  }
  if (services.size === 0) {
    continue;
  }
  const serviceNames = [...services].join('|');
  const fields = new Set();
  for (const match of source.matchAll(
    new RegExp(`(\\w+)\\s*=\\s*inject\\(\\s*(${serviceNames})\\s*\\)`, 'g'),
  )) {
    fields.add(match[1]);
  }
  for (const match of source.matchAll(new RegExp(`(\\w+)\\s*:\\s*(${serviceNames})\\b`, 'g'))) {
    fields.add(match[1]);
  }
  if (fields.size === 0) {
    continue;
  }
  const allowed = callRanges(source, ['loadResource', 'runMutation']);
  const callPattern = new RegExp(`\\b(?:this\\.)?(${[...fields].join('|')})\\.(\\w+)\\s*\\(`, 'g');
  for (const match of source.matchAll(callPattern)) {
    const inside = allowed.some(([start, end]) => match.index > start && match.index < end);
    if (!inside) {
      violations.push(
        `${file}:${lineOf(source, match.index)}: ${match[1]}.${match[2]}() must be called inside loadResource(...) or runMutation(...)`,
      );
    }
  }
}

if (violations.length > 0) {
  console.error(violations.join('\n'));
  console.error(`\nDataState check failed: ${violations.length} direct API call(s) in features/.`);
  process.exit(1);
}
console.log(`DataState check passed (${files.length} feature files).`);
