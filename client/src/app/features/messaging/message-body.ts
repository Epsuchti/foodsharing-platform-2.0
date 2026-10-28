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

/** Replaces the trailing `@query` with the mention token and a space. */
export function insertMention(text: string, name: string, userId: string): string {
  return text.replace(/@([^\s@[\]()]{0,30})$/, `${mentionToken(name, userId)} `);
}
