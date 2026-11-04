/** A piece of a chat message body: plain text, a mention token `@[Name](user:<uuid>)` or a link. */
export type BodySegment =
  | { kind: 'text'; text: string }
  | { kind: 'mention'; name: string; userId: string }
  | { kind: 'link'; href: string };

const TOKEN = /@\[([^\]\n]{1,80})\]\(user:([0-9a-f-]{36})\)|(https?:\/\/[^\s<>"')\]]+)/gi;

/** Splits a message body for rendering (mentions as user chips, http(s) links auto-linked, everything else text). */
export function bodySegments(body: string): BodySegment[] {
  const segments: BodySegment[] = [];
  let last = 0;
  for (const match of body.matchAll(TOKEN)) {
    const index = match.index ?? 0;
    if (index > last) {
      segments.push({ kind: 'text', text: body.slice(last, index) });
    }
    if (match[3]) {
      // a trailing sentence dot or comma is not part of the link
      const href = match[3].replace(/[.,;:!?]+$/, '');
      segments.push({ kind: 'link', href });
      last = index + href.length;
      continue;
    }
    segments.push({ kind: 'mention', name: match[1], userId: match[2] });
    last = index + match[0].length;
  }
  if (last < body.length) {
    segments.push({ kind: 'text', text: body.slice(last) });
  }
  return segments;
}

/** Mention token for a user (A36 forum format, shared by messaging). */
export function mentionToken(name: string, userId: string): string {
  return `@[${name.replace(/[[\]\n]/g, '')}](user:${userId})`;
}

/** The `@query` being typed at the end of `text` (for the mention autocomplete), or null. */
export function mentionQuery(text: string): string | null {
  const match = /(?:^|\s)@([^\s@[\]()]{0,30})$/.exec(text);
  return match ? match[1] : null;
}

/** Name as it appears in a token (brackets and newlines removed, see {@link mentionToken}). */
function tokenName(name: string): string {
  return name.replace(/[[\]\n]/g, '');
}

/**
 * Replaces the trailing `@query` with the plain `@Name` and a space (decision 161: the composer shows plain text; the
 * tokens are built on send by {@link encodeMentions}).
 */
export function insertMention(text: string, name: string): string {
  return text.replace(/@([^\s@[\]()]{0,30})$/, () => `@${tokenName(name)} `);
}

const escapeRegExp = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/**
 * Turns the plain `@Name` of every picked person (name → user id) into a mention token. Longest names first, so
 * "@Ana Muster" wins over "@Ana"; only `@Name` at a word start and followed by the end, a space or punctuation counts.
 * A hand-edited name keeps its plain text (accepted, plan risk).
 */
export function encodeMentions(text: string, picked: ReadonlyMap<string, string>): string {
  const names = [...picked.keys()].map(tokenName).filter((name) => name.length > 0);
  if (names.length === 0) return text;
  const ids = new Map([...picked].map(([name, id]) => [tokenName(name), id]));
  names.sort((a, b) => b.length - a.length);
  const pattern = new RegExp(`(?<![\\p{L}\\p{N}_@])@(${names.map(escapeRegExp).join('|')})(?=$|[\\s.,;:!?)\\]}'"»…-])`, 'gu');
  return text.replace(pattern, (_match, name: string) => mentionToken(name, ids.get(name) ?? ''));
}

/** The editable form of a stored body: tokens back to `@Name`, and the people they name (for {@link encodeMentions}). */
export function decodeMentions(body: string): { text: string; picked: Map<string, string> } {
  const picked = new Map<string, string>();
  const text = bodySegments(body)
    .map((segment) => {
      if (segment.kind === 'mention') {
        picked.set(segment.name, segment.userId);
        return `@${segment.name}`;
      }
      return segment.kind === 'text' ? segment.text : segment.href;
    })
    .join('');
  return { text, picked };
}
