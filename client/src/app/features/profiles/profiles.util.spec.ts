import { Banana, BuddyRelation, PublicProfile, Role } from '../../api';
import {
  BANANA_MIN_LENGTH,
  ACHIEVEMENT_ICON_CHOICES,
  achievementIcon,
  activeTab,
  buddyActions,
  mayDeleteBanana,
  photoInSize,
  profileTabs,
} from './profiles.util';

const permissions = (overrides: Partial<PublicProfile['permissions']> = {}): PublicProfile['permissions'] => ({
  mayEditProfile: false,
  maySeeNotes: false,
  mayReport: false,
  mayGiveBanana: false,
  maySeeHistory: false,
  maySeeCommitments: false,
  mayAdminister: false,
  maySeePickups: false,
  ...overrides,
});

const profile = (overrides: Partial<PublicProfile['permissions']> = {}): PublicProfile =>
  ({ permissions: permissions(overrides) }) as PublicProfile;

const user = (id: string) => ({ id, displayName: id, role: Role.Foodsaver, verified: true });

describe('profiles util', () => {
  it('keeps the banana minimum of the server (ProfileRules.BANANA_MIN_LENGTH)', () => {
    expect(BANANA_MIN_LENGTH).toBe(100);
  });

  it('offers the tabs the permissions allow, wall and bananas always', () => {
    expect(profileTabs(profile())).toEqual(['wall', 'bananas']);
    expect(profileTabs(profile({ maySeeCommitments: true }))).toEqual(['wall', 'bananas', 'commitments']);
    expect(profileTabs(profile({ maySeeCommitments: true, maySeeNotes: true, maySeeHistory: true }))).toEqual([
      'wall',
      'bananas',
      'commitments',
      'notes',
      'history',
    ]);
  });

  it('falls back to the wall for unknown or forbidden tabs', () => {
    expect(activeTab(profile(), 'bananas')).toBe('bananas');
    expect(activeTab(profile(), 'notes')).toBe('wall');
    expect(activeTab(profile({ maySeeNotes: true }), 'notes')).toBe('notes');
    expect(activeTab(profile(), 'nonsense')).toBe('wall');
    expect(activeTab(profile(), null)).toBe('wall');
  });

  it('switches upload photos to the requested variant size and keeps other URLs', () => {
    expect(photoInSize('/api/uploads/abc?size=md', 'lg')).toBe('/api/uploads/abc?size=lg');
    expect(photoInSize('/api/uploads/abc', 'sm')).toBe('/api/uploads/abc?size=sm');
    expect(photoInSize('https://example.org/me.png', 'lg')).toBe('https://example.org/me.png');
    expect(photoInSize(undefined, 'lg')).toBeUndefined();
  });

  it('maps the buddy relation to the viewer actions (A16)', () => {
    expect(buddyActions(BuddyRelation.None, false)).toEqual(['request']);
    expect(buddyActions(BuddyRelation.RequestedByMe, false)).toEqual(['withdraw']);
    expect(buddyActions(BuddyRelation.RequestedByThem, false)).toEqual(['accept', 'remove']);
    expect(buddyActions(BuddyRelation.Confirmed, false)).toEqual(['remove']);
    expect(buddyActions(BuddyRelation.None, true)).toEqual([]);
  });

  it('lets recipient, sender and ORGA delete a banana', () => {
    const banana: Banana = { sender: user('s'), recipient: user('r'), message: 'x', givenAt: '2026-11-01T09:00:00Z' };
    expect(mayDeleteBanana(banana, 'r', false)).toBe(true);
    expect(mayDeleteBanana(banana, 's', false)).toBe(true);
    expect(mayDeleteBanana(banana, 'other', false)).toBe(false);
    expect(mayDeleteBanana(banana, undefined, false)).toBe(false);
    expect(mayDeleteBanana(banana, 'other', true)).toBe(true);
  });

  it('maps achievement icons to PrimeIcons with a trophy fallback', () => {
    expect(achievementIcon('shield')).toBe('pi pi-shield');
    expect(achievementIcon('cpu')).toBe('pi pi-microchip');
    expect(achievementIcon('pi-truck')).toBe('pi pi-truck');
    expect(achievementIcon('unknown-thing')).toBe('pi pi-trophy');
    expect(achievementIcon(undefined)).toBe('pi pi-trophy');
  });

  it('renders every icon of the achievement picker as itself', () => {
    for (const name of ACHIEVEMENT_ICON_CHOICES) {
      expect(achievementIcon(name)).toBe(`pi pi-${name}`);
    }
    expect(achievementIcon('crown')).toBe('pi pi-crown');
  });
});
