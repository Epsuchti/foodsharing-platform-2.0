import { map } from 'rxjs';

import { ForumService, ReactionKey } from '../../api';
import { HistoryFilterValue, HistoryPageRequest, HistorySource } from '../../shared/history-timeline/history-timeline.types';

/** History source of the moderation log (G3, D10.3); the timeline's action filter applies to the loaded page. */
export function moderationLogSource(api: ForumService, regionId: string): HistorySource {
  return (filter: HistoryFilterValue, page: HistoryPageRequest) =>
    api.getModerationLog({ regionId, from: filter.from, to: filter.to, page: page.page, size: page.size }).pipe(
      map((result) =>
        filter.actions.length ? { ...result, items: result.items.filter((entry) => filter.actions.includes(entry.action)) } : result,
      ),
    );
}

/** Emoji shown for each allowed reaction key (A36 allow-list, upstream forum reactions). */
export const REACTION_EMOJI: Readonly<Record<ReactionKey, string>> = {
  thumbsup: '👍',
  thumbsdown: '👎',
  heart: '❤️',
  laughing: '😆',
  open_mouth: '😮',
  cry: '😢',
  angry: '😠',
  tada: '🎉',
  pray: '🙏',
  eyes: '👀',
  rocket: '🚀',
  '100': '💯',
};

export const REACTION_KEYS = Object.keys(REACTION_EMOJI) as ReactionKey[];

/**
 * Reply-with-quote (F3): `> **Name** wrote:` followed by the quoted body (each line prefixed with `> `) and an empty
 * line for the answer. Nested quotes of the original are dropped so quotes do not pile up.
 */
export function quotePost(name: string, body: string, wroteLabel: string, existing = ''): string {
  const lines = body
    .split('\n')
    .filter((line) => !line.startsWith('>'))
    .join('\n')
    .trim()
    .split('\n')
    .map((line) => `> ${line}`.trimEnd());
  const quote = [`> **${name.replace(/[*\n]/g, '')}** ${wroteLabel}`, ...lines].join('\n');
  const prefix = existing.trim() ? `${existing.trimEnd()}\n\n` : '';
  return `${prefix}${quote}\n\n`;
}

/** Mention tokens `@[Name](user:<uuid>)` (A37a) as bold markdown names, so `fs-markdown` renders them safely. */
export function renderMentions(body: string): string {
  return body.replace(/@\[([^\]\n]{1,100})\]\(user:[0-9a-f-]{36}\)/g, (_m, name: string) => `**@${name}**`);
}

/** Highlights search terms in a snippet: returns text pieces with a `match` flag (no HTML injection). */
export function highlight(snippet: string, query: string): { text: string; match: boolean }[] {
  const terms = query
    .trim()
    .split(/\s+/)
    .filter((term) => term.length > 1)
    .map((term) => term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  if (!terms.length) return [{ text: snippet, match: false }];
  const pattern = new RegExp(`(${terms.join('|')})`, 'gi');
  return snippet
    .split(pattern)
    .filter((part) => part !== '')
    .map((part) => ({ text: part, match: isMatch(part, terms) }));
}

function isMatch(part: string, terms: string[]): boolean {
  return terms.some((term) => new RegExp(`^${term}$`, 'i').test(part));
}
