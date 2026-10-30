import { SessionInfo, UnitMembership, WorkingGroupFunction } from '../../api';

/** Where a post stands for its editors (C2). */
export type BlogStatus = 'published' | 'awaitingCheck' | 'draft';

export function blogStatus(post: { published: boolean; awaitingCheck?: boolean }): BlogStatus {
  if (post.published) {
    return 'published';
  }
  return post.awaitingCheck ? 'awaitingCheck' : 'draft';
}

/**
 * Blog editors (A54, server `BlogService.isEditor`): ORGA or admins of a PR working group. They publish directly;
 * the client copy only decides what to show — the server answers 403 to everything else.
 */
export function isBlogEditor(session: SessionInfo | null | undefined, isOrga: boolean): boolean {
  return (
    isOrga ||
    (session?.units ?? []).some((unit) => unit.admin && unit.function === WorkingGroupFunction.Pr)
  );
}

/** Regions and groups the user administrates: ambassadors write posts for them (server `adminRegionIds`). */
export function blogUnits(session: SessionInfo | null | undefined): UnitMembership[] {
  return (session?.units ?? [])
    .filter((unit) => unit.admin)
    .sort((a, b) => a.name.localeCompare(b.name));
}

/** May open the blog management (editors and ambassadors). */
export function mayWriteBlog(session: SessionInfo | null | undefined, isOrga: boolean): boolean {
  return isBlogEditor(session, isOrga) || blogUnits(session).length > 0;
}

/** A picture URL of the server (`/api/uploads/<id>?size=lg`) in another variant (A41: sm | md | lg). */
export function pictureVariant(url: string | null | undefined, size: 'sm' | 'md' | 'lg'): string | null {
  if (!url) {
    return null;
  }
  return /[?&]size=/.test(url)
    ? url.replace(/([?&]size=)[a-z]+/, `$1${size}`)
    : `${url}${url.includes('?') ? '&' : '?'}size=${size}`;
}

const TEASER_LENGTH = 280;

/**
 * Teaser from a Markdown body when the author leaves it empty: plain text of the first paragraphs, cut at a word
 * boundary with an ellipsis.
 */
export function teaserFromBody(body: string, max = TEASER_LENGTH): string {
  const plain = body
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/[*_`>~]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  if (plain.length <= max) {
    return plain;
  }
  const cut = plain.slice(0, max);
  const space = cut.lastIndexOf(' ');
  return `${(space > max * 0.6 ? cut.slice(0, space) : cut).trimEnd()}…`;
}
