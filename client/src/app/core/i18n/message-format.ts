/**
 * Minimal ICU-style message formatting:
 *  - `{name}` interpolation
 *  - `{count, plural, =0 {none} one {# item} other {# items}}` with Intl.PluralRules (`#` = formatted count)
 *  - `{kind, select, a {…} other {…}}`
 * Unknown placeholders stay visible as `{name}` so missing params are easy to spot.
 */
export type MessageParams = Record<string, unknown>;

const pluralRulesCache = new Map<string, Intl.PluralRules>();
const numberFormatCache = new Map<string, Intl.NumberFormat>();

/**
 * Small counts (integers 0–999) without Intl (D11.5.3): creating the first `Intl.NumberFormat` / `Intl.PluralRules`
 * of a locale loads its ICU data — ~115 ms at 4× CPU inside the first page render, before `/onboarding`'s LCP.
 * For these values every UI locale (de-CH, en, fr-CH, it-CH) formats the plain digits and the plural category is
 * `one` for 1 (French: 0 and 1) and `other` otherwise; message-format.spec.ts checks this against Intl.
 */
function isSmallCount(value: number): boolean {
  return Number.isInteger(value) && value >= 0 && value < 1000;
}

function smallCountCategory(value: number, locale: string): Intl.LDMLPluralRule {
  const french = locale === 'fr' || locale.startsWith('fr-');
  return value === 1 || (french && value === 0) ? 'one' : 'other';
}

/** Locales whose small counts follow {@link smallCountCategory}; anything else always goes through Intl. */
const SMALL_COUNT_LOCALES = /^(de|en|fr|it)(-|$)/;

function pluralCategory(value: number, locale: string): Intl.LDMLPluralRule {
  if (isSmallCount(value) && SMALL_COUNT_LOCALES.test(locale)) {
    return smallCountCategory(value, locale);
  }
  return pluralRules(locale).select(value);
}

function pluralRules(locale: string): Intl.PluralRules {
  let rules = pluralRulesCache.get(locale);
  if (!rules) {
    rules = new Intl.PluralRules(locale);
    pluralRulesCache.set(locale, rules);
  }
  return rules;
}

function formatNumber(value: number, locale: string): string {
  if (isSmallCount(value) && SMALL_COUNT_LOCALES.test(locale)) {
    return String(value);
  }
  let format = numberFormatCache.get(locale);
  if (!format) {
    format = new Intl.NumberFormat(locale);
    numberFormatCache.set(locale, format);
  }
  return format.format(value);
}

/** Index of the `}` closing the `{` at `open`, or -1. */
function matchingBrace(text: string, open: number): number {
  let depth = 0;
  for (let i = open; i < text.length; i++) {
    if (text[i] === '{') {
      depth++;
    } else if (text[i] === '}') {
      depth--;
      if (depth === 0) {
        return i;
      }
    }
  }
  return -1;
}

/** Parses `=0 {…} one {…} other {…}` into a map. */
function parseOptions(text: string): Map<string, string> {
  const options = new Map<string, string>();
  let i = 0;
  while (i < text.length) {
    while (i < text.length && /\s/.test(text[i])) {
      i++;
    }
    const selectorStart = i;
    while (i < text.length && text[i] !== '{' && !/\s/.test(text[i])) {
      i++;
    }
    const selector = text.slice(selectorStart, i);
    while (i < text.length && /\s/.test(text[i])) {
      i++;
    }
    if (!selector || text[i] !== '{') {
      break;
    }
    const close = matchingBrace(text, i);
    if (close < 0) {
      break;
    }
    options.set(selector, text.slice(i + 1, close));
    i = close + 1;
  }
  return options;
}

/** Replaces `#` that is not inside a nested `{…}` block. */
function replaceHash(text: string, value: string): string {
  let depth = 0;
  let result = '';
  for (const char of text) {
    if (char === '{') depth++;
    if (char === '}') depth--;
    result += char === '#' && depth === 0 ? value : char;
  }
  return result;
}

export function formatMessage(
  template: string,
  params: MessageParams | undefined,
  locale: string,
): string {
  if (!template.includes('{')) {
    return template;
  }
  let result = '';
  let i = 0;
  while (i < template.length) {
    const open = template.indexOf('{', i);
    if (open < 0) {
      result += template.slice(i);
      break;
    }
    result += template.slice(i, open);
    const close = matchingBrace(template, open);
    if (close < 0) {
      result += template.slice(open);
      break;
    }
    result += formatPlaceholder(template.slice(open + 1, close), params, locale);
    i = close + 1;
  }
  return result;
}

function formatPlaceholder(
  inner: string,
  params: MessageParams | undefined,
  locale: string,
): string {
  const complex = /^\s*([a-zA-Z_]\w*)\s*,\s*(plural|select)\s*,([\s\S]*)$/.exec(inner);
  if (!complex) {
    const name = inner.trim();
    const value = params?.[name];
    if (value === undefined || value === null) {
      return `{${name}}`;
    }
    return typeof value === 'number' ? formatNumber(value, locale) : String(value);
  }
  const [, name, kind, body] = complex;
  const options = parseOptions(body);
  const raw = params?.[name];
  if (kind === 'select') {
    const chosen = options.get(String(raw)) ?? options.get('other') ?? '';
    return formatMessage(chosen, params, locale);
  }
  const count = typeof raw === 'number' ? raw : Number(raw);
  if (Number.isNaN(count)) {
    return formatMessage(options.get('other') ?? '', params, locale);
  }
  const chosen =
    options.get(`=${count}`) ??
    options.get(pluralCategory(count, locale)) ??
    options.get('other') ??
    '';
  return formatMessage(replaceHash(chosen, formatNumber(count, locale)), params, locale);
}
