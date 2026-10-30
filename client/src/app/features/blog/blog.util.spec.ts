import { SessionInfo, UnitMembership, UnitType, WorkingGroupFunction } from '../../api';
import {
  blogStatus,
  blogUnits,
  isBlogEditor,
  mayWriteBlog,
  pictureVariant,
  teaserFromBody,
} from './blog.util';

const unit = (name: string, admin: boolean, fn?: WorkingGroupFunction): UnitMembership => ({
  id: `id-${name}`,
  name,
  slug: name.toLowerCase(),
  type: fn ? UnitType.WorkingGroup : UnitType.City,
  member: true,
  admin,
  homeRegion: false,
  function: fn,
});

const session = (...units: UnitMembership[]): SessionInfo =>
  ({ authenticated: true, units }) as unknown as SessionInfo;

describe('blog rules (Q11, C2)', () => {
  it('status: published wins, then awaiting check, else draft', () => {
    expect(blogStatus({ published: true, awaitingCheck: true })).toBe('published');
    expect(blogStatus({ published: false, awaitingCheck: true })).toBe('awaitingCheck');
    expect(blogStatus({ published: false })).toBe('draft');
  });

  it('editors are ORGA and admins of a PR group; PR members and other admins are not', () => {
    expect(isBlogEditor(session(), true)).toBe(true);
    expect(isBlogEditor(session(unit('PR', true, WorkingGroupFunction.Pr)), false)).toBe(true);
    expect(isBlogEditor(session(unit('PR', false, WorkingGroupFunction.Pr)), false)).toBe(false);
    expect(isBlogEditor(session(unit('Welcome', true, WorkingGroupFunction.Welcome)), false)).toBe(false);
    expect(isBlogEditor(null, false)).toBe(false);
  });

  it('ambassadors write for the units they administrate, sorted by name', () => {
    const s = session(unit('Zürich', true), unit('Basel', false), unit('Begrüssung', true, WorkingGroupFunction.Welcome));
    expect(blogUnits(s).map((u) => u.name)).toEqual(['Begrüssung', 'Zürich']);
    expect(mayWriteBlog(s, false)).toBe(true);
    expect(mayWriteBlog(session(unit('Basel', false)), false)).toBe(false);
    expect(mayWriteBlog(session(), true)).toBe(true);
  });

  it('picture variants replace or add the size', () => {
    expect(pictureVariant('/api/uploads/a?size=lg', 'md')).toBe('/api/uploads/a?size=md');
    expect(pictureVariant('/api/uploads/a', 'sm')).toBe('/api/uploads/a?size=sm');
    expect(pictureVariant('/api/uploads/a?x=1', 'sm')).toBe('/api/uploads/a?x=1&size=sm');
    expect(pictureVariant(undefined, 'md')).toBeNull();
  });

  it('teaser from the body: plain text, cut at a word with an ellipsis', () => {
    expect(teaserFromBody('# Titel\n\nEin **kurzer** [Text](https://x.ch). ![Bild](/a.png)')).toBe(
      'Titel Ein kurzer Text.',
    );
    const long = teaserFromBody('wort '.repeat(100), 40);
    expect(long.endsWith('…')).toBe(true);
    expect(long.length).toBeLessThanOrEqual(41);
    expect(long).not.toContain('wor…');
  });
});
