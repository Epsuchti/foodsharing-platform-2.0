#!/usr/bin/env node
// Feature coverage gate (plan §18.3 "coverage rule", plan-m1 T5).
//
// Compares the IDs in docs/ledger.md (feature table: §13/§14 rows; deliverable table: D<section>.<n>) with the
// `@ID` tags of the Playwright journeys and writes docs/coverage.md. An ID whose rules are proven below e2e (decision
// 180) passes with an entry in e2e/coverage-map.json instead: `{ "EA20": "server/src/test/java/…/IntroProgressJobTest.java" }`
// (a path or a list of paths, relative to the repository); the gate fails when a named file does not exist.
//
// Usage:
//   node e2e/scripts/feature-coverage.mjs                 # list mode: `playwright test --list --reporter=json`
//   node e2e/scripts/feature-coverage.mjs --results [file] # results mode: a JSON report (default e2e/test-results/results.json)
//   options: --strict (M7: every feature row needs a test)  --ledger <file>  --map <file>  --out <file>  --no-write  --quiet
//
// Exit 1 when
//   (a) a ledger row with status `done` has neither a passing tagged test (list mode: no tagged test at all) nor a
//       coverage-map entry,
//   (b) a test carries a tag that is neither a ledger ID nor an allowed meta tag,
//   (c) --strict and a feature row has neither a tagged test nor a coverage-map entry,
//   (d) a coverage-map entry names an unknown ID or a file that does not exist.
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const E2E_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const REPO_DIR = path.resolve(E2E_DIR, '..');

/** Tags that are not ledger IDs. Keep in sync with docs/conventions.md A8 and playwright.config.ts. */
export const META_TAGS = new Set(['page', 'screenshot', 'chromium-only', 'smoke', 'slow', 'journey', 'infra', 'gallery']);
/** Meta tags that mark a test as deliberately feature-less (test infrastructure, component gallery). */
const NON_FEATURE_TAGS = new Set(['infra', 'gallery']);
const ID_PATTERN = /^[A-Z][A-Za-z]*\d+(?:\.\d+)*$/;

function parseArgs(argv) {
  const args = {
    strict: false,
    write: true,
    quiet: false,
    results: undefined,
    ledger: 'docs/ledger.md',
    map: 'e2e/coverage-map.json',
    out: 'docs/coverage.md',
  };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    const next = () => (argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[++i] : undefined);
    if (arg === '--strict') args.strict = true;
    else if (arg === '--no-write') args.write = false;
    else if (arg === '--quiet') args.quiet = true;
    else if (arg === '--results') args.results = next() ?? 'e2e/test-results/results.json';
    else if (arg === '--ledger') args.ledger = next();
    else if (arg === '--map') args.map = next();
    else if (arg === '--out') args.out = next();
    else if (arg === '--help' || arg === '-h') {
      console.log(fs.readFileSync(fileURLToPath(import.meta.url), 'utf8').split('\n').slice(1, 17).join('\n'));
      process.exit(0);
    } else throw new Error(`Unknown argument ${arg}`);
  }
  return args;
}

/** Parses the `| ID | … | Status | …` tables of the ledger. */
export function parseLedger(markdown) {
  const rows = [];
  let heading = '';
  let header;
  for (const line of markdown.split('\n')) {
    if (line.startsWith('#')) {
      heading = line.replace(/^#+\s*/, '');
      header = undefined;
      continue;
    }
    if (!line.trim().startsWith('|')) {
      header = undefined;
      continue;
    }
    const cells = line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((cell) => cell.trim());
    if (!header) {
      if (cells[0] === 'ID') header = cells.map((cell) => cell.toLowerCase());
      continue;
    }
    if (cells.every((cell) => /^:?-+:?$/.test(cell))) continue;
    const row = Object.fromEntries(header.map((name, index) => [name, cells[index] ?? '']));
    if (!ID_PATTERN.test(row.id)) continue;
    rows.push({
      id: row.id,
      title: (row.feature ?? row.deliverable ?? '').replace(/\*\*/g, ''),
      status: (row.status ?? '').toLowerCase(),
      kind: /deliverable/i.test(heading) ? 'deliverable' : 'feature',
      notes: row.notes ?? '',
    });
  }
  return rows;
}

/** Flattens a Playwright JSON report (list or results) into one entry per test and project. */
export function collectTests(report) {
  const tests = [];
  const visit = (suite, file) => {
    for (const spec of suite.specs ?? []) {
      for (const test of spec.tests ?? []) {
        tests.push({
          file: spec.file ?? file,
          title: spec.title,
          tags: (spec.tags ?? []).map((tag) => tag.replace(/^@/, '')),
          project: test.projectName,
          status: test.results?.length ? test.status : 'listed',
        });
      }
    }
    for (const child of suite.suites ?? []) visit(child, child.file ?? file);
  };
  for (const suite of report.suites ?? []) visit(suite, suite.file);
  return tests;
}

function listTests() {
  // Run the CLI through node itself: Windows refuses to spawn the .cmd shim without a shell (EINVAL).
  const cli = path.join(E2E_DIR, 'node_modules', '@playwright', 'test', 'cli.js');
  const result = spawnSync(process.execPath, [cli, 'test', '--list', '--reporter=json'], {
    cwd: E2E_DIR,
    env: { ...process.env, E2E_SCREENSHOTS: '1', FORCE_COLOR: '0' },
    encoding: 'utf8',
    maxBuffer: 256 * 1024 * 1024,
  });
  if (result.error) throw result.error;
  const start = result.stdout.indexOf('{');
  if (start < 0) throw new Error(`playwright --list produced no JSON:\n${result.stderr}`);
  const report = JSON.parse(result.stdout.slice(start));
  if (report.errors?.length) {
    throw new Error(`playwright --list reported errors:\n${report.errors.map((error) => error.message).join('\n')}`);
  }
  return report;
}

/**
 * Reads the coverage map (`{ "ID": "path" | ["path", …] }`, paths relative to `repoDir`). Returns the entries as
 * `Map<id, string[]>` and a problem for every file that does not exist. A missing map file is an empty map.
 */
export function readCoverageMap(file, repoDir = REPO_DIR, exists = fs.existsSync) {
  const map = new Map();
  const problems = [];
  if (!exists(file)) return { map, problems };
  const raw = JSON.parse(fs.readFileSync(file, 'utf8'));
  for (const [id, value] of Object.entries(raw)) {
    if (id.startsWith('$')) continue; // "$comment"
    const files = Array.isArray(value) ? value : [value];
    if (!files.length || files.some((entry) => typeof entry !== 'string' || !entry)) {
      problems.push(`coverage map: ${id} needs a file path`);
      continue;
    }
    for (const entry of files) {
      if (!exists(path.resolve(repoDir, entry))) problems.push(`coverage map: ${id} names ${entry}, which does not exist`);
    }
    map.set(id, files);
  }
  return { map, problems };
}

const isPass = (status) => status === 'expected' || status === 'flaky';
const isScreenshotProject = (project) => project?.startsWith('screenshots-');

export function evaluate(rows, tests, { strict, mode, map = new Map() }) {
  const ids = new Map(rows.map((row) => [row.id, row]));
  const byId = new Map(rows.map((row) => [row.id, { e2e: new Set(), screenshot: new Set(), pass: 0, fail: 0 }]));
  const unknownTags = new Map();
  const untagged = new Set();
  for (const test of tests) {
    if (test.project === 'setup') continue;
    const key = `${test.file} › ${test.title}`;
    const idTags = test.tags.filter((tag) => !META_TAGS.has(tag));
    if (!idTags.length && !test.tags.some((tag) => NON_FEATURE_TAGS.has(tag))) untagged.add(key);
    for (const tag of idTags) {
      if (!ids.has(tag)) {
        if (!unknownTags.has(tag)) unknownTags.set(tag, new Set());
        unknownTags.get(tag).add(key);
        continue;
      }
      const entry = byId.get(tag);
      (isScreenshotProject(test.project) ? entry.screenshot : entry.e2e).add(key);
      if (mode === 'results') {
        if (isPass(test.status)) entry.pass++;
        else if (test.status === 'unexpected') entry.fail++;
      }
    }
  }

  const problems = [];
  for (const id of map.keys()) {
    if (!ids.has(id)) problems.push(`coverage map: ${id} is not in docs/ledger.md`);
  }
  for (const [tag, where] of unknownTags) {
    problems.push(`unknown tag @${tag} (not in docs/ledger.md) in: ${[...where].join('; ')}`);
  }
  const lines = [];
  for (const row of rows) {
    const entry = byId.get(row.id);
    const total = entry.e2e.size + entry.screenshot.size;
    const mapped = map.get(row.id) ?? [];
    let result;
    if (total === 0) result = mapped.length ? 'unit/integration' : 'missing';
    else if (mode === 'list') result = 'listed';
    else if (entry.fail > 0) result = entry.pass > 0 ? 'partly failing' : 'failing';
    else if (entry.pass > 0) result = 'passing';
    else result = 'not run';
    // Build-time deliverables (ArchUnit, lint, config) are proven by the Maven build, not by Playwright:
    // the ledger notes say "proved by <test>" and the e2e column stays empty.
    const buildProven = row.kind === 'deliverable' && /^proved by /i.test((row.notes ?? '').trim());
    if (row.status === 'done' && !buildProven && !mapped.length) {
      const ok = mode === 'list' ? total > 0 : entry.pass > 0;
      if (!ok) problems.push(`${row.id} is done in the ledger but has no ${mode === 'list' ? '' : 'passing '}tagged test and no coverage-map entry`);
    }
    if (strict && row.kind === 'feature' && total === 0 && !mapped.length) {
      problems.push(`${row.id} has no tagged test and no coverage-map entry (--strict)`);
    }
    lines.push({ ...row, e2e: entry.e2e.size, screenshot: entry.screenshot.size, mapped, pass: entry.pass, fail: entry.fail, result });
  }
  return { lines, problems, untagged: [...untagged] };
}

function renderMarkdown({ lines, problems, untagged }, { mode, source, strict }) {
  const features = lines.filter((line) => line.kind === 'feature');
  const deliverables = lines.filter((line) => line.kind === 'deliverable');
  const covered = (list) => list.filter((line) => line.result !== 'missing').length;
  const table = (list, label) =>
    [
      `| ID | ${label} | Ledger | E2E tests | Screenshot tests | Unit/integration | Result |`,
      '|---|---|---|---|---|---|---|',
      ...list.map(
        (line) =>
          `| ${line.id} | ${line.title.replace(/\|/g, '\\|')} | ${line.status || '-'} | ${line.e2e} | ${line.screenshot} | ${
            line.mapped.map((file) => `\`${path.basename(file)}\``).join(', ') || '-'
          } | ${line.result}${
            mode === 'results' && (line.pass || line.fail) ? ` (${line.pass} passed, ${line.fail} failed)` : ''
          } |`,
      ),
    ].join('\n');
  return [
    '# Feature coverage',
    '',
    `Generated by \`e2e/scripts/feature-coverage.mjs\` (${mode} mode${strict ? ', strict' : ''}, source: ${source}). Do not edit by hand.`,
    '',
    `- Features with a tagged test or a coverage-map entry: **${covered(features)} / ${features.length}**`,
    `- Deliverables with a tagged test or a coverage-map entry: **${covered(deliverables)} / ${deliverables.length}**`,
    `- Gate: ${problems.length ? `**FAILED** (${problems.length} problem${problems.length === 1 ? '' : 's'})` : 'passed'}`,
    '',
    ...(problems.length ? ['## Problems', '', ...problems.map((problem) => `- ${problem}`), ''] : []),
    ...(untagged.length ? ['## Tests without an ID tag', '', ...untagged.map((test) => `- ${test}`), ''] : []),
    '## Features (§13, §14)',
    '',
    table(features, 'Feature'),
    '',
    '## Deliverables',
    '',
    table(deliverables, 'Deliverable'),
    '',
  ].join('\n');
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const ledgerPath = path.resolve(REPO_DIR, args.ledger);
  const rows = parseLedger(fs.readFileSync(ledgerPath, 'utf8'));
  if (!rows.length) throw new Error(`No ID rows found in ${ledgerPath}`);

  const mode = args.results ? 'results' : 'list';
  let report;
  let source;
  if (mode === 'results') {
    const resultsPath = path.resolve(REPO_DIR, args.results);
    report = JSON.parse(fs.readFileSync(resultsPath, 'utf8'));
    source = path.relative(REPO_DIR, resultsPath);
  } else {
    report = listTests();
    source = 'playwright test --list';
  }
  const coverageMap = readCoverageMap(path.resolve(REPO_DIR, args.map));
  const evaluation = evaluate(rows, collectTests(report), { strict: args.strict, mode, map: coverageMap.map });
  evaluation.problems.unshift(...coverageMap.problems);
  const markdown = renderMarkdown(evaluation, { mode, source, strict: args.strict });
  if (args.write) {
    const outPath = path.resolve(REPO_DIR, args.out);
    fs.mkdirSync(path.dirname(outPath), { recursive: true });
    fs.writeFileSync(outPath, markdown);
  }

  if (!args.quiet) {
    const width = Math.max(...evaluation.lines.map((line) => line.id.length));
    for (const line of evaluation.lines.filter((entry) => entry.result !== 'missing' || entry.status === 'done')) {
      console.log(`${line.id.padEnd(width)}  ${line.result.padEnd(14)} e2e ${line.e2e}  screenshot ${line.screenshot}  [${line.status}]`);
    }
    const features = evaluation.lines.filter((line) => line.kind === 'feature');
    console.log(
      `\nFeatures covered: ${features.filter((line) => line.result !== 'missing').length}/${features.length}; ` +
        `untagged tests: ${evaluation.untagged.length}${args.write ? `; wrote ${args.out}` : ''}`,
    );
  }
  if (evaluation.problems.length) {
    console.error(`\nFeature coverage gate failed:\n${evaluation.problems.map((problem) => `  - ${problem}`).join('\n')}`);
    process.exit(1);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    main();
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exit(2);
  }
}
