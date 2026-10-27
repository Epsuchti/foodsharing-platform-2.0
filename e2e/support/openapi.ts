import fs from 'node:fs';
import path from 'node:path';
import { parse } from 'yaml';
import { OPENAPI_DIR } from './env';

interface SchemaObject {
  type?: string;
  required?: string[];
  properties?: Record<string, SchemaObject & { $ref?: string }>;
  enum?: string[];
  items?: SchemaObject & { $ref?: string };
  $ref?: string;
}

const cache = new Map<string, Record<string, SchemaObject>>();

/** A named schema from `server/src/main/resources/openapi/schemas/<file>.yaml` (the contract the jar implements). */
export function schema(file: string, name: string): SchemaObject {
  if (!cache.has(file)) {
    cache.set(file, parse(fs.readFileSync(path.join(OPENAPI_DIR, 'schemas', `${file}.yaml`), 'utf8')) ?? {});
  }
  const found = cache.get(file)![name];
  if (!found) throw new Error(`Schema ${name} not found in schemas/${file}.yaml`);
  return found;
}

/**
 * Shallow structural check of a JSON value against an object schema: required properties present, primitive types
 * and enum values right, `$ref`s to other object schemas checked recursively. Returns a list of problems.
 */
export function conformance(value: unknown, file: string, name: string, at = name): string[] {
  return check(value, schema(file, name), file, at);
}

function check(value: unknown, s: SchemaObject, file: string, at: string): string[] {
  if (s.$ref) {
    const [refFile, refName] = resolveRef(s.$ref, file);
    return check(value, schema(refFile, refName), refFile, at);
  }
  if (s.enum) return s.enum.includes(value as string) ? [] : [`${at}: ${JSON.stringify(value)} not in ${s.enum.join('|')}`];
  switch (s.type) {
    case 'string':
      return typeof value === 'string' ? [] : [`${at}: expected string`];
    case 'boolean':
      return typeof value === 'boolean' ? [] : [`${at}: expected boolean`];
    case 'integer':
    case 'number':
      return typeof value === 'number' ? [] : [`${at}: expected number`];
    case 'array':
      if (!Array.isArray(value)) return [`${at}: expected array`];
      return s.items ? value.flatMap((item, index) => check(item, s.items!, file, `${at}[${index}]`)) : [];
    default: {
      if (typeof value !== 'object' || value === null || Array.isArray(value)) return [`${at}: expected object`];
      const record = value as Record<string, unknown>;
      const problems = (s.required ?? []).filter((key) => record[key] === undefined).map((key) => `${at}.${key}: missing`);
      for (const [key, property] of Object.entries(s.properties ?? {})) {
        if (record[key] !== undefined && record[key] !== null) problems.push(...check(record[key], property, file, `${at}.${key}`));
      }
      return problems;
    }
  }
}

function resolveRef(ref: string, currentFile: string): [string, string] {
  const [filePart, pointer] = ref.split('#/');
  const file = filePart ? path.basename(filePart, '.yaml') : currentFile;
  return [file, pointer];
}
