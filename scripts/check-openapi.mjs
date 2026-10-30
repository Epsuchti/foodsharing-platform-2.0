#!/usr/bin/env node
// Contract rules for the split OpenAPI spec (docs/conventions.md A5).
//
//   node scripts/check-openapi.mjs [--skip-validate] [--spec-dir <dir>]
//
// 1. `openapi-generator-cli validate` on openapi.yaml (generator version pinned in client/openapitools.json, the single pin).
// 2. Every path item in paths/<tag>.yaml is referenced from openapi.yaml under the same path (and vice versa).
// 3. Every operation: exactly one tag == file name, unique operationId, `x-permission`, every 4xx/5xx response is
//    `application/json` with `$ref` to common.yaml#/ApiError; mutating operations (post/put/patch/delete) have
//    `x-history` (`<Module>Action.<VALUE>` or `NONE`); `dev` operations are marked `x-dev-only: true`.
// 4. Declared tags == files in paths/; every local `$ref` points to an existing file and top-level key.
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';

const REPO_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const METHODS = ['get', 'put', 'post', 'delete', 'options', 'head', 'patch', 'trace'];
const MUTATING = new Set(['post', 'put', 'patch', 'delete']);
const HISTORY_PATTERN = /^(NONE|[A-Z][A-Za-z]*Action\.[A-Z][A-Z0-9_]*)$/;

const args = process.argv.slice(2);
const skipValidate = args.includes('--skip-validate');
const specDirArg = args.indexOf('--spec-dir');
const SPEC_DIR = path.resolve(REPO_DIR, specDirArg >= 0 ? args[specDirArg + 1] : 'server/src/main/resources/openapi');
const ROOT = path.join(SPEC_DIR, 'openapi.yaml');

const errors = [];
const warnings = [];
// Paths in messages are relative to the spec directory (openapi.yaml, paths/<tag>.yaml, schemas/<tag>.yaml).
const rel = (file) => path.relative(SPEC_DIR, file);
const yamlCache = new Map();

function load(file) {
  if (!yamlCache.has(file)) yamlCache.set(file, parse(fs.readFileSync(file, 'utf8')) ?? {});
  return yamlCache.get(file);
}

const decodePointer = (pointer) => pointer.replace(/~1/g, '/').replace(/~0/g, '~');

function validateWithGenerator() {
  // the CLI's main.js on this Node: a .cmd shim cannot be spawned without a shell on Windows (EINVAL)
  const cli = path.join(REPO_DIR, 'node_modules/@openapitools/openapi-generator-cli/main.js');
  if (!fs.existsSync(cli)) {
    errors.push('openapi-generator-cli is not installed: run `npm ci` in the repository root');
    return;
  }
  const result = spawnSync(process.execPath, [cli, '--openapitools', path.join(REPO_DIR, 'client/openapitools.json'), 'validate', '-i', ROOT], {
    cwd: REPO_DIR,
    encoding: 'utf8',
  });
  const output = `${result.stdout ?? ''}${result.stderr ?? ''}${result.error ? String(result.error) : ''}`.trim();
  if (result.status !== 0) errors.push(`openapi-generator validate failed:\n${output}`);
  else console.log(output.split('\n').filter((line) => /validat|issue/i.test(line)).join('\n'));
}

function checkRefs(node, file, where) {
  if (Array.isArray(node)) {
    node.forEach((item, index) => checkRefs(item, file, `${where}[${index}]`));
    return;
  }
  if (!node || typeof node !== 'object') return;
  for (const [key, value] of Object.entries(node)) {
    if (key === '$ref' && typeof value === 'string') {
      const [target, pointer = ''] = value.split('#');
      if (/^https?:/.test(target)) continue;
      const targetFile = target ? path.resolve(path.dirname(file), target) : file;
      if (!fs.existsSync(targetFile)) {
        errors.push(`${rel(file)} ${where}: $ref ${value} → missing file ${rel(targetFile)}`);
        continue;
      }
      const segments = pointer.split('/').filter(Boolean).map(decodePointer);
      let current = load(targetFile);
      for (const segment of segments) current = current?.[segment];
      if (current === undefined) errors.push(`${rel(file)} ${where}: $ref ${value} → not found in ${rel(targetFile)}`);
    } else {
      checkRefs(value, file, `${where}.${key}`);
    }
  }
}

function isApiErrorRef(ref) {
  return typeof ref === 'string' && /(^|\/)common\.yaml#\/ApiError$/.test(ref);
}

function checkOperation(op, method, pathKey, tag, file, operationIds) {
  const where = `${rel(file)} ${method.toUpperCase()} ${pathKey}`;
  if (!Array.isArray(op.tags) || op.tags.length !== 1 || op.tags[0] !== tag) {
    errors.push(`${where}: tags must be exactly [${tag}] (tag == file name), got ${JSON.stringify(op.tags)}`);
  }
  if (!op.operationId) errors.push(`${where}: missing operationId`);
  else if (operationIds.has(op.operationId)) {
    errors.push(`${where}: duplicate operationId ${op.operationId} (also ${operationIds.get(op.operationId)})`);
  } else operationIds.set(op.operationId, where);
  if (!op['x-permission'] || typeof op['x-permission'] !== 'string') errors.push(`${where}: missing x-permission`);
  if (MUTATING.has(method)) {
    const history = op['x-history'];
    if (!history) errors.push(`${where}: mutating operation without x-history (use <Module>Action.<VALUE> or NONE)`);
    else if (!HISTORY_PATTERN.test(String(history))) {
      errors.push(`${where}: x-history "${history}" must be <Module>Action.<VALUE> or NONE`);
    }
  }
  if (tag === 'dev' && op['x-dev-only'] !== true) errors.push(`${where}: dev operations need x-dev-only: true`);

  const responses = op.responses ?? {};
  if (!Object.keys(responses).length) errors.push(`${where}: no responses`);
  const errorStatuses = Object.keys(responses).filter((status) => /^[45]/.test(status));
  if (!errorStatuses.some((status) => status.startsWith('4'))) warnings.push(`${where}: documents no 4xx response`);
  for (const status of errorStatuses) {
    const ref = responses[status]?.content?.['application/json']?.schema?.$ref;
    if (!isApiErrorRef(ref)) {
      errors.push(`${where}: response ${status} must be application/json with $ref to common.yaml#/ApiError`);
    }
  }
}

function main() {
  if (!fs.existsSync(ROOT)) throw new Error(`${ROOT} not found`);
  if (!skipValidate) validateWithGenerator();

  const root = load(ROOT);
  const pathsDir = path.join(SPEC_DIR, 'paths');
  const tagFiles = fs
    .readdirSync(pathsDir)
    .filter((name) => name.endsWith('.yaml'))
    .map((name) => name.replace(/\.yaml$/, ''))
    .sort();
  const declaredTags = (root.tags ?? []).map((tag) => tag.name);
  for (const tag of tagFiles) if (!declaredTags.includes(tag)) errors.push(`paths/${tag}.yaml: tag "${tag}" is not declared in openapi.yaml tags`);
  for (const tag of declaredTags) if (!tagFiles.includes(tag)) errors.push(`openapi.yaml: tag "${tag}" has no paths/${tag}.yaml`);

  // openapi.yaml → path files
  const referenced = new Map(); // "<file>#<pathKey>" → root path
  for (const [rootPath, item] of Object.entries(root.paths ?? {})) {
    const ref = item?.$ref;
    if (!ref || Object.keys(item).length !== 1) {
      errors.push(`openapi.yaml ${rootPath}: must be a single $ref into paths/<tag>.yaml`);
      continue;
    }
    const match = /^\.\/paths\/([a-z0-9-]+)\.yaml#\/(.+)$/.exec(ref);
    if (!match) {
      errors.push(`openapi.yaml ${rootPath}: $ref ${ref} must look like ./paths/<tag>.yaml#/<escaped path>`);
      continue;
    }
    const [, tag, pointer] = match;
    const pathKey = decodePointer(pointer);
    if (pathKey !== rootPath) errors.push(`openapi.yaml ${rootPath}: $ref points to a different path (${pathKey})`);
    const file = path.join(pathsDir, `${tag}.yaml`);
    if (!fs.existsSync(file)) {
      errors.push(`openapi.yaml ${rootPath}: ${rel(file)} does not exist`);
      continue;
    }
    if (!load(file)[pathKey]) errors.push(`openapi.yaml ${rootPath}: ${pathKey} not found in paths/${tag}.yaml`);
    referenced.set(`${tag}#${pathKey}`, rootPath);
  }

  // path files → operations
  const operationIds = new Map();
  let operations = 0;
  for (const tag of tagFiles) {
    const file = path.join(pathsDir, `${tag}.yaml`);
    const doc = load(file);
    checkRefs(doc, file, '#');
    for (const [pathKey, item] of Object.entries(doc)) {
      if (!pathKey.startsWith('/')) {
        errors.push(`${rel(file)}: top-level key "${pathKey}" is not a path`);
        continue;
      }
      if (!referenced.has(`${tag}#${pathKey}`)) errors.push(`${rel(file)} ${pathKey}: not referenced from openapi.yaml`);
      for (const method of METHODS) {
        if (!item?.[method]) continue;
        operations++;
        checkOperation(item[method], method, pathKey, tag, file, operationIds);
      }
    }
  }
  const schemasDir = path.join(SPEC_DIR, 'schemas');
  for (const name of fs.readdirSync(schemasDir).filter((file) => file.endsWith('.yaml'))) {
    const file = path.join(schemasDir, name);
    checkRefs(load(file), file, '#');
  }
  checkRefs(root, ROOT, '#');

  for (const warning of warnings) console.warn(`warning: ${warning}`);
  if (errors.length) {
    console.error(`\nOpenAPI contract check failed (${errors.length}):\n${errors.map((error) => `  - ${error}`).join('\n')}`);
    process.exit(1);
  }
  console.log(`OpenAPI contract check passed: ${operations} operations in ${tagFiles.length} tags (${path.relative(REPO_DIR, SPEC_DIR)}).`);
}

main();
